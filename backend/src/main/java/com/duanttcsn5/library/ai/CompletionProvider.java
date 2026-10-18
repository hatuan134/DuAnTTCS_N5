package com.duanttcsn5.library.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** OpenAI-compatible protocol implemented by Groq and Gemini. Never logs response bodies or API keys. */
abstract class CompletionProvider implements AiProvider {
    private static final Logger LOG=LoggerFactory.getLogger(CompletionProvider.class);
    private final String name;
    private final URI endpoint;
    protected final AiSettings settings;
    private final ObjectMapper json;
    private final HttpClient http;
    CompletionProvider(String name, URI endpoint, AiSettings settings, ObjectMapper json, HttpClient http) {
        this.name=name; this.endpoint=endpoint; this.settings=settings; this.json=json; this.http=http;
    }
    public String name() { return name; }
    public boolean configured() { return !settings.key(name).isBlank(); }
    private HttpRequest request(String model,List<Map<String,Object>> messages, List<Map<String,Object>> tools, long deadlineNanos,boolean stream) {
        if (!configured()) throw new Failure(false);
        long remaining=TimeUnit.NANOSECONDS.toMillis(deadlineNanos-System.nanoTime());
        if(remaining<250) throw new Failure(true);
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("model",model); body.put("messages",messages);
        body.put("max_tokens",settings.maxTokens()); body.put("stream",stream);
        if(!tools.isEmpty()) {body.put("tools",tools);body.put("tool_choice","auto");}
        if(name.equals("GEMINI")) body.put("reasoning_effort","low");
        if(name.equals("GROQ") && model.startsWith("openai/gpt-oss-")) {
            // GPT-OSS defaults to medium reasoning, often wasteful for quick LIBRA queries.
            body.put("reasoning_effort","low");
            body.put("reasoning_format","hidden");
        }
        return HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMillis(Math.min(remaining,settings.timeoutSeconds()*1000L)))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer " + settings.key(name))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }
    private void ensureStatus(int status,HttpHeaders headers) {
        if(status==200) return;
        // Status only, no request, response body, bearer token, names or business data.
        LOG.warn("LIBRA_AI provider={} returned HTTP {} (check model, quota, timeout or credentials)",name,status);
        long retryAfterMillis=0;
        if(status==429 || status==503) {
            try {
                String seconds=headers.firstValue("Retry-After").orElse("");
                retryAfterMillis=Math.multiplyExact(Long.parseLong(seconds.trim()),1000L);
            } catch(NumberFormatException | ArithmeticException ignored) {
                // HTTP Retry-After may also be an RFC-1123 date.
                try {
                    var retryAt=java.time.ZonedDateTime.parse(
                        headers.firstValue("Retry-After").orElse(""),
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    retryAfterMillis=Math.max(0L,java.time.Duration.between(java.time.Instant.now(),retryAt).toMillis());
                } catch(java.time.DateTimeException invalid) {
                    retryAfterMillis=0;
                }
            }
        }
        throw new Failure(status==408 || status==429 || status>=500,status,retryAfterMillis);
    }
    public JsonNode complete(List<Map<String,Object>> messages, List<Map<String,Object>> tools, long deadlineNanos) {
        return completeWithModel(settings.model(name),messages,tools,deadlineNanos);
    }
    public JsonNode completeWithModel(String model,List<Map<String,Object>> messages, List<Map<String,Object>> tools, long deadlineNanos) {
        try {
            var response=http.send(request(model,messages,tools,deadlineNanos,false),HttpResponse.BodyHandlers.ofString());
            ensureStatus(response.statusCode(),response.headers());
            if(response.body().length()>128000) throw new Failure(false);
            var result=json.readTree(response.body()).path("choices").path(0);
            String finish=result.path("finish_reason").asText();
            if("length".equals(finish) || "content_filter".equals(finish)) throw new Failure(false);
            var message=result.path("message");
            if(!message.isObject() || (!message.path("content").isTextual() && !message.path("tool_calls").isArray())) throw new Failure(false);
            return message;
        } catch(Failure e) {throw e;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new Failure(false);
        } catch(java.net.http.HttpTimeoutException e) {LOG.warn("LIBRA_AI provider={} request timed out",name);throw new Failure(true);
        } catch(IOException e) {LOG.warn("LIBRA_AI provider={} network failure: {}",name,e.getClass().getSimpleName());throw new Failure(true);
        } catch(RuntimeException e) {LOG.warn("LIBRA_AI provider={} invalid or unsupported completion response: {}",name,e.getClass().getSimpleName());throw new Failure(false);}
    }
    /**
     * Real provider-level SSE: used only for tool-free completions. Model tool calls
     * are always processed with the non-streaming, validated tool path.
     */
    @Override
    public JsonNode completeStreaming(List<Map<String,Object>> messages,long deadlineNanos,Consumer<String> onDelta) {
        return completeStreamingWithModel(settings.model(name),messages,deadlineNanos,onDelta);
    }
    public JsonNode completeStreamingWithModel(String model,List<Map<String,Object>> messages,long deadlineNanos,Consumer<String> onDelta) {
        StringBuilder full=new StringBuilder();
        try {
            var response=http.send(request(model,messages,List.of(),deadlineNanos,true),HttpResponse.BodyHandlers.ofInputStream());
            ensureStatus(response.statusCode(),response.headers());
            String finish="";
            int bytes=0;
            try(var reader=new BufferedReader(new InputStreamReader(response.body(),StandardCharsets.UTF_8))) {
                String line;
                while((line=reader.readLine())!=null) {
                    if(System.nanoTime()>=deadlineNanos) throw new Failure(true);
                    if(!line.startsWith("data:")) continue;
                    String data=line.substring(5).trim();
                    if(data.equals("[DONE]")) break;
                    if(data.isEmpty()) continue;
                    bytes+=data.length();
                    if(bytes>256_000) throw new Failure(false);
                    var choice=json.readTree(data).path("choices").path(0);
                    var delta=choice.path("delta");
                    var content=delta.path("content");
                    if(content.isTextual()) {
                        String chunk=content.asText();
                        if(!chunk.isEmpty()) {
                            full.append(chunk);
                            if(full.length()>12_000) throw new Failure(false);
                            onDelta.accept(chunk);
                        }
                    }
                    if(choice.path("finish_reason").isTextual()) finish=choice.path("finish_reason").asText();
                }
            }
            if(full.isEmpty() || "length".equals(finish) || "content_filter".equals(finish)) throw new Failure(false);
            return json.readTree(json.writeValueAsString(Map.of("role","assistant","content",full.toString())));
        } catch(Failure e) {throw e;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new Failure(false);
        } catch(java.net.http.HttpTimeoutException e) {LOG.warn("LIBRA_AI provider={} stream timed out",name);throw new Failure(true);
        } catch(IOException e) {LOG.warn("LIBRA_AI provider={} stream network failure: {}",name,e.getClass().getSimpleName());throw new Failure(true);
        } catch(RuntimeException e) {LOG.warn("LIBRA_AI provider={} malformed SSE stream: {}",name,e.getClass().getSimpleName());throw new Failure(false);}
    }
    static HttpClient client() {return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();}
}
