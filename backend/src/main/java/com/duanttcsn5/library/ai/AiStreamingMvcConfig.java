package com.duanttcsn5.library.ai;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiStreamingMvcConfig implements WebMvcConfigurer {
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Keep a JWT-authenticated SSE chat alive while backend tools and AI complete.
        configurer.setDefaultTimeout(75_000);
    }
}
