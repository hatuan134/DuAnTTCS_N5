package com.duanttcsn5.library.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;

@Component
public class GeminiProvider extends CompletionProvider {
    @Autowired
    public GeminiProvider(AiSettings settings, ObjectMapper json) {
        super("GEMINI", URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"), settings, json, client());
    }
    // Package-private transport injection for loopback mock-server tests only.
    GeminiProvider(AiSettings settings, ObjectMapper json, URI endpoint, HttpClient client) {
        super("GEMINI", endpoint, settings, json, client);
    }
}
