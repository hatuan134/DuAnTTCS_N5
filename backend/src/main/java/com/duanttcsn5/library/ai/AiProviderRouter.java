package com.duanttcsn5.library.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Attempts a bounded, operator-configured model list. Failure of one model does
 * not disable other models. Retry-After is honored per model (it cannot bypass
 * a provider-wide quota). Read access is enforced separately by AiTools.
 *
 * Private cross-company transfer needs BOTH opt-ins: AI_ALLOW_CROSS_PROVIDER_FALLBACK
 * and AI_ALLOW_PRIVATE_CONTEXT. Never print prompts, API keys or model responses.
 */
@Component
public class AiProviderRouter {
    private static final Logger LOG=LoggerFactory.getLogger(AiProviderRouter.class);
    private final AiSettings settings;
    private final GroqProvider groq;
    private final GeminiProvider gemini;
    private final LongSupplier nowMillis;
    private final Map<String,Circuit> health=new ConcurrentHashMap<>();

    private static final class Circuit {
        final AtomicInteger failures=new AtomicInteger();
        final AtomicLong cooldownUntil=new AtomicLong();
        final AtomicBoolean probe=new AtomicBoolean();
    }

    @Autowired
    public AiProviderRouter(AiSettings settings,GroqProvider groq,GeminiProvider gemini) {
        this(settings,groq,gemini,System::currentTimeMillis);
    }
    AiProviderRouter(AiSettings settings,GroqProvider groq,GeminiProvider gemini,LongSupplier nowMillis) {
        this.settings=settings;this.groq=groq;this.gemini=gemini;this.nowMillis=nowMillis;
    }

    public AiProvider select(String message) {
        AiProvider selected=switch(settings.mode()) {
            case GROQ_ONLY, GROQ_FIRST -> groq;
            case GEMINI_ONLY, GEMINI_FIRST -> gemini;
            case AUTO -> message.toLowerCase(Locale.ROOT).matches("(?s).*(phân tích|thống kê|báo cáo|so sánh).*") && gemini.configured()
                ? gemini : groq.configured() ? groq : gemini;
        };
        // FIRST/AUTO may begin without a primary key and use a configured backup.
        if(!selected.configured() && !(settings.fallbackAllowed() && crossVendorMode()
            && other(selected).configured())) throw new AiProvider.Failure(false,401,0);
        return selected;
    }

    public record Answer(AiProvider provider,JsonNode message) {}
    public Answer call(AiProvider selected,List<Map<String,Object>> messages,List<Map<String,Object>> tools,
                       boolean sensitive,long deadline) {
        return call(selected,messages,tools,sensitive,deadline,null);
    }
    public Answer call(AiProvider selected,List<Map<String,Object>> messages,List<Map<String,Object>> tools,
                       boolean sensitive,long deadline,Consumer<String> onDelta) {
        boolean streaming=onDelta!=null && tools.isEmpty();
        boolean maySwitch=settings.fallbackAllowed() && crossVendorMode()
            && (!sensitive || settings.privateContextAllowed());
        List<AiProvider> vendors=new ArrayList<>(List.of(selected));
        AiProvider backup=other(selected);
        // The selected provider may change after a successful tool-call round.
        // Permit switching in either direction; per-model cooldown prevents loops.
        if(maySwitch) vendors.add(backup); // Check backup configuration only after primary failure.

        AiProvider.Failure last=null;
        long earliestWait=Long.MAX_VALUE;
        for(AiProvider vendor:vendors) {
            if(!vendor.configured()) continue;
            boolean invalidCredentials=false;
            for(String model:settings.models(vendor.name())) {
                if(System.nanoTime()>=deadline) throw last!=null?last:new AiProvider.Failure(true);
                Circuit circuit=health.computeIfAbsent(vendor.name()+":"+model,k->new Circuit());
                long until=circuit.cooldownUntil.get();
                boolean probe=false;
                if(until>nowMillis.getAsLong()) {
                    earliestWait=Math.min(earliestWait,until-nowMillis.getAsLong());
                    continue;
                }
                if(until!=0) {
                    probe=circuit.probe.compareAndSet(false,true);
                    if(!probe) continue;
                }
                // Buffer every model's text until that attempt finishes successfully.
                // Otherwise a partial SSE reply prevents safe retry with another model.
                StringBuilder buffered=new StringBuilder();
                try {
                    JsonNode result=complete(vendor,model,messages,tools,deadline,streaming,buffered::append);
                    circuit.failures.set(0);
                    circuit.cooldownUntil.set(0);
                    if(streaming && !buffered.isEmpty()) onDelta.accept(buffered.toString());
                    if(probe) LOG.info("LIBRA_AI recovered provider={} model={}",vendor.name(),model);
                    return new Answer(vendor,result);
                } catch(AiProvider.Failure failure) {
                    last=failure;
                    boolean unavailable=failure.retryable() || Set.of(400,401,403,404,422).contains(failure.statusCode());
                    if(!unavailable) throw failure; // malformed response etc. is not a quota event
                    if(failure.retryable()) {
                        int count=circuit.failures.incrementAndGet();
                        if(failure.statusCode()==429 || count>=settings.modelFailureThreshold()) {
                            long minCooldown=(vendor==groq?settings.groqCooldownSeconds():settings.geminiCooldownSeconds())*1000L;
                            long cooldown=Math.min(900_000L,Math.max(minCooldown,failure.retryAfterMillis()));
                            circuit.cooldownUntil.set(nowMillis.getAsLong()+cooldown);
                            earliestWait=Math.min(earliestWait,cooldown);
                        }
                    } else {
                        // Bad/retired model: do not retry this model immediately, but try other models.
                        circuit.cooldownUntil.set(nowMillis.getAsLong()+60_000L);
                        if(failure.statusCode()==401 || failure.statusCode()==403) invalidCredentials=true;
                    }
                    LOG.warn("LIBRA_AI unavailable provider={} model={} status={}; trying next configured model",
                        vendor.name(),model,failure.statusCode());
                } finally {
                    if(probe) circuit.probe.set(false);
                }
                if(invalidCredentials) break; // key is shared by models of one vendor
            }
        }
        if(last!=null) {
            if(last.statusCode()==429 && earliestWait!=Long.MAX_VALUE)
                throw new AiProvider.Failure(true,429,earliestWait);
            throw last;
        }
        if(earliestWait!=Long.MAX_VALUE) throw new AiProvider.Failure(true,429,earliestWait);
        throw new AiProvider.Failure(false,401,0);
    }

    private boolean crossVendorMode() {
        return settings.mode()==AiSettings.Mode.GROQ_FIRST || settings.mode()==AiSettings.Mode.GEMINI_FIRST
            || settings.mode()==AiSettings.Mode.AUTO;
    }
    private AiProvider other(AiProvider selected) { return selected==groq?gemini:groq; }
    private JsonNode complete(AiProvider vendor,String model,List<Map<String,Object>> messages,
                              List<Map<String,Object>> tools,long deadline,boolean streaming,Consumer<String> buffer) {
        // Legacy single-model calls preserve compatibility with existing integrations.
        boolean primaryModel=model.equals(settings.model(vendor.name()));
        if(streaming) {
            if(!primaryModel && vendor instanceof CompletionProvider compatible)
                return compatible.completeStreamingWithModel(model,messages,deadline,buffer);
            return vendor.completeStreaming(messages,deadline,buffer);
        }
        if(!primaryModel && vendor instanceof CompletionProvider compatible)
            return compatible.completeWithModel(model,messages,tools,deadline);
        return vendor.complete(messages,tools,deadline);
    }
}
