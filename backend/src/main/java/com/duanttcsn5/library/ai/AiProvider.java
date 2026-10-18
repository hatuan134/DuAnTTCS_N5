package com.duanttcsn5.library.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;

public interface AiProvider {
    String name();
    boolean configured();
    JsonNode complete(List<Map<String,Object>> messages, List<Map<String,Object>> tools, long deadlineNanos);
    default JsonNode completeStreaming(List<Map<String,Object>> messages,long deadlineNanos,Consumer<String> onDelta) {
        var response=complete(messages,List.of(),deadlineNanos);
        if(response.path("content").isTextual()) onDelta.accept(response.path("content").asText());
        return response;
    }
    class Failure extends RuntimeException {
        private final boolean retryable;
        private final int statusCode;
        private final long retryAfterMillis;
        public Failure(boolean retryable) { this(retryable,0,0); }
        public Failure(boolean retryable,int statusCode,long retryAfterMillis) {
            super("Dịch vụ AI tạm thời không phản hồi.");
            this.retryable=retryable;
            this.statusCode=statusCode;
            this.retryAfterMillis=Math.max(0,retryAfterMillis);
        }
        public boolean retryable() { return retryable; }
        public int statusCode() { return statusCode; }
        public long retryAfterMillis() { return retryAfterMillis; }
    }
}
