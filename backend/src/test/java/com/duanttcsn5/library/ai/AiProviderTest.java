package com.duanttcsn5.library.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AiProviderTest {
    HttpServer server;
    ExecutorService executor;
    String requestBody, authorization;
    int code=200;
    String retryAfter="";
    String response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Xin chào\"}}]}";
    long delay=0;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/chat", exchange->{
            requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            authorization=exchange.getRequestHeaders().getFirst("Authorization");
            try { if(delay>0)Thread.sleep(delay); byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
                if(!retryAfter.isBlank()) exchange.getResponseHeaders().set("Retry-After",retryAfter);
                exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);
            } catch(InterruptedException e){Thread.currentThread().interrupt();} finally{exchange.close();}
        });server.start();
    }
    @AfterEach void stop() { server.stop(0);executor.shutdownNow(); }
    AiProvider provider(String name) {
        var settings=new AiSettings(new MockEnvironment().withProperty(name+"_API_KEY","mock-local-key"));
        var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat");
        return name.equals("GROQ")?new GroqProvider(settings,JsonMapper.builder().build(),uri,HttpClient.newHttpClient()):new GeminiProvider(settings,JsonMapper.builder().build(),uri,HttpClient.newHttpClient());
    }
    @ParameterizedTest @ValueSource(strings={"GROQ","GEMINI"}) void sendsCorrectModelAndAuthorization(String name) {
        var out=provider(name).complete(List.of(Map.of("role","user","content","Xin chào")),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
        assertThat(out.path("content").asText()).isEqualTo("Xin chào");
        assertThat(authorization).isEqualTo("Bearer mock-local-key");
        assertThat(requestBody).contains(name.equals("GROQ")?"openai/gpt-oss-120b":"gemini-2.5-flash").contains("max_tokens").doesNotContain("mock-local-key");
    }
    @ParameterizedTest @ValueSource(strings={"GROQ","GEMINI"}) void parsesFunctionCallsAndPreservesProviderMetadata(String name) {
        response="{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"searchBooks\",\"arguments\":\"{}\"},\"extra_content\":{\"google\":{\"thought_signature\":\"opaque\"}}}]}}]}";
        var out=provider(name).complete(List.of(),List.of(Map.of("type","function")),System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
        assertThat(out.path("tool_calls").get(0).path("function").path("name").asText()).isEqualTo("searchBooks");
        assertThat(out.toString()).contains("thought_signature"); assertThat(requestBody).contains("tool_choice");
    }
    @ParameterizedTest @ValueSource(ints={429,500,503,401,400}) void hidesErrorBody(int status) {
        code=status;response="provider internal secret data";
        assertThatThrownBy(()->provider("GROQ").complete(List.of(),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5)))
            .isInstanceOf(AiProvider.Failure.class).hasMessageNotContaining("secret");
    }
    @Test void groqRateLimitRetainsHttp429AndRetryAfterForCaller() {
        code=429;
        retryAfter="42";
        response="provider internal secret data";
        assertThatThrownBy(()->provider("GROQ").complete(List.of(),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5)))
            .isInstanceOfSatisfying(AiProvider.Failure.class,failure->{
                assertThat(failure.statusCode()).isEqualTo(429);
                assertThat(failure.retryable()).isTrue();
                assertThat(failure.retryAfterMillis()).isEqualTo(42_000L);
                assertThat(failure.getMessage()).doesNotContain("secret");
            });
    }
    @Test void geminiRateLimitMayUseHttpDateRetryAfter() {
        code=429;
        retryAfter=java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
            java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120));
        assertThatThrownBy(()->provider("GEMINI").complete(List.of(),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5)))
            .isInstanceOfSatisfying(AiProvider.Failure.class,failure->{
                assertThat(failure.statusCode()).isEqualTo(429);
                assertThat(failure.retryAfterMillis()).isBetween(90_000L,121_000L);
            });
    }
    @Test void boundedTimeout() {
        delay=1000;
        long start=System.nanoTime();
        assertThatThrownBy(()->provider("GEMINI").complete(List.of(),List.of(),start+TimeUnit.MILLISECONDS.toNanos(200))).isInstanceOf(AiProvider.Failure.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1000);
    }
    @Test void malformedOrTruncatedResponsesRejected() {
        response="not json";
        assertThatThrownBy(()->provider("GROQ").complete(List.of(),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5))).isInstanceOf(AiProvider.Failure.class);
        response="{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}";
        assertThatThrownBy(()->provider("GROQ").complete(List.of(),List.of(),System.nanoTime()+TimeUnit.SECONDS.toNanos(5))).isInstanceOf(AiProvider.Failure.class);
    }
}
