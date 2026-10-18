package com.duanttcsn5.library.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;

@Component
public class GroqProvider extends CompletionProvider {
    @Autowired
    public GroqProvider(AiSettings settings, ObjectMapper json) {
        super("GROQ", URI.create("https://api.groq.com/openai/v1/chat/completions"), settings, json, client());
    }
    // Package-private transport injection for loopback mock-server tests only.
    GroqProvider(AiSettings settings, ObjectMapper json, URI endpoint, HttpClient client) {
        super("GROQ", endpoint, settings, json, client);
    }
}
