package com.duanttcsn5.library.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class AiProviderStreamingTest {
    @Test void groqSseChunksAreDeliveredInOrder() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",request->{
            var requestBody=new String(request.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(!requestBody.contains("\"stream\":true")) {request.sendResponseHeaders(400,-1);request.close();return;}
            byte[] payload=("data: {\"choices\":[{\"delta\":{\"content\":\"Xin \"}}]}\n\n"+
                "data: {\"choices\":[{\"delta\":{\"content\":\"chào\"},\"finish_reason\":\"stop\"}]}\n\n"+
                "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().add("Content-Type","text/event-stream");
            request.sendResponseHeaders(200,payload.length);
            request.getResponseBody().write(payload);
            request.close();
        });
        server.start();
        try {
            var settings=new AiSettings(new MockEnvironment().withProperty("GROQ_API_KEY","local-test-only")
                .withProperty("GROQ_MODEL","openai/gpt-oss-120b"));
            var provider=new GroqProvider(settings,JsonMapper.builder().build(),
                URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat"),HttpClient.newHttpClient());
            var chunks=new ArrayList<String>();
            var response=provider.completeStreaming(List.of(Map.of("role","user","content","Hi")),
                System.nanoTime()+TimeUnit.SECONDS.toNanos(8),chunks::add);
            assertThat(chunks).containsExactly("Xin ","chào");
            assertThat(response.path("content").asText()).isEqualTo("Xin chào");
        } finally {server.stop(0);}
    }
}
