package com.duanttcsn5.library.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Read only explicitly named environment keys; never expose this object in an API response. */
@Component
public class AiSettings {
    public enum Mode { AUTO, GROQ_ONLY, GEMINI_ONLY, GROQ_FIRST, GEMINI_FIRST }
    private final Environment env;
    public AiSettings(Environment env) { this.env = env; }
    public boolean enabled() { return flag("AI_ENABLED", false); }
    public Mode mode() { return Mode.valueOf(env.getProperty("AI_PROVIDER_MODE", "AUTO").trim().toUpperCase(java.util.Locale.ROOT)); }
    public String key(String provider) { return env.getProperty(provider + "_API_KEY", "").trim(); }
    public String model(String provider) { return env.getProperty(provider + "_MODEL", provider.equals("GROQ") ? "openai/gpt-oss-120b" : "gemini-2.5-flash"); }
    /** Ordered models to try for a vendor. Supports GROQ_MODELS / GEMINI_MODELS.
     * Legacy GROQ_MODEL / GEMINI_MODEL remain valid for single-model deployments.
     * These are administrator-controlled identifiers, never supplied by a chat user.
     */
    public java.util.List<String> models(String provider) {
        String raw=env.getProperty(provider+"_MODELS","").trim();
        if(raw.isBlank()) raw=model(provider);
        java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>();
        for(String part:raw.split(",")) {
            String name=part.trim();
            if(!name.isEmpty() && name.length()<=100 && name.matches("[a-zA-Z0-9_./:+-]+")) result.add(name);
            if(result.size()>=6) break;
        }
        if(result.isEmpty()) result.add(model(provider));
        return java.util.List.copyOf(result);
    }
    /** Explicit consent is needed to send private authenticated context to external AI services. */
    public boolean privateProviderSwitchAllowed() { return fallbackAllowed() && privateContextAllowed(); }
    public int totalTimeoutSeconds() { return number("AI_TOTAL_TIMEOUT_SECONDS",90,15,180); }
    public int geminiCooldownSeconds() { return number("AI_GEMINI_COOLDOWN_SECONDS",60,10,900); }
    // Fallback copies user-authored content to a second company; explicit operator opt-in required.
    public int groqCooldownSeconds() { return number("AI_GROQ_COOLDOWN_SECONDS", 60, 10, 900); }
    public int groqFailureThreshold() { return number("AI_GROQ_FAILURE_THRESHOLD", 2, 1, 5); }
    public int modelFailureThreshold() { return number("AI_MODEL_FAILURE_THRESHOLD",groqFailureThreshold(),1,5); }
    public boolean fallbackAllowed() { return flag("AI_ALLOW_CROSS_PROVIDER_FALLBACK", false); }
    public boolean privateContextAllowed() { return flag("AI_ALLOW_PRIVATE_CONTEXT", false); }
    public int timeoutSeconds() { return number("AI_TIMEOUT_SECONDS", 25, 3, 45); }
    public int maxTokens() { return number("AI_MAX_TOKENS", 1200, 256, 2048); }
    public int maxTools() { return number("AI_MAX_TOOLS", 4, 1, 6); }
    public int guestLimit() { return number("AI_GUEST_REQUESTS_PER_MINUTE", 6, 0, 1000); }
    public int userLimit() { return number("AI_USER_REQUESTS_PER_MINUTE", 15, 0, 1000); }
    public int globalLimit() { return number("AI_GLOBAL_REQUESTS_PER_MINUTE",120,0,5000); }
    public int turnLimit() { return number("AI_MAX_TURNS_PER_CONVERSATION",40,0,1000); }
    public int concurrency() { return number("AI_MAX_CONCURRENT", 6, 1, 20); }
    private boolean flag(String key, boolean fallback) { return Boolean.parseBoolean(env.getProperty(key, String.valueOf(fallback))); }
    private int number(String key,int fallback,int min,int max) { return Math.max(min,Math.min(max,Integer.parseInt(env.getProperty(key,String.valueOf(fallback))))); }
}
