package com.duanttcsn5.library.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AiTypes {
    private AiTypes() {}
    public record ChatRequest(@NotBlank @Size(max=1500) String message,
                              @Size(max=36) String conversationId, @Size(max=120) String page) {}
    public record Action(String label, String href) {}
    public record Block(String type, String title, List<Map<String,Object>> items,
                        Map<String,Object> meta, List<Action> actions) {}
    public record ChatResponse(String conversationId, String message, List<Block> blocks,
                               String provider, boolean degraded, int turnsRemaining) {}
    public record ToolResult(Block block, Object providerData, boolean sensitive) {}
}
