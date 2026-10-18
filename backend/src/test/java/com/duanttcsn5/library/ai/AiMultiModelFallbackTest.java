package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Provider and model fallback tests; no real vendor calls or database required. */
class AiMultiModelFallbackTest {
    private final GroqProvider groq=mock(GroqProvider.class);
    private final GeminiProvider gemini=mock(GeminiProvider.class);
    private final AtomicLong millis=new AtomicLong(1_000_000);
    private final JsonNode ok=JsonMapper.builder().build().readTree("{\"role\":\"assistant\",\"content\":\"ok\"}");
    private long deadline() { return System.nanoTime()+TimeUnit.SECONDS.toNanos(3); }
    private AiProviderRouter router(boolean allowPrivate,String mode) {
        // Mockito mocks do not execute CompletionProvider.name(): unstubbed it is null.
        // The router uses the provider name to look up its model list and cooldown.
        when(groq.name()).thenReturn("GROQ");
        when(gemini.name()).thenReturn("GEMINI");
        when(groq.configured()).thenReturn(true);
        when(gemini.configured()).thenReturn(true);
        var settings=new AiSettings(new MockEnvironment()
            .withProperty("AI_PROVIDER_MODE",mode)
            .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK","true")
            .withProperty("AI_ALLOW_PRIVATE_CONTEXT",String.valueOf(allowPrivate))
            .withProperty("GROQ_MODEL","groq-one")
            .withProperty("GROQ_MODELS","groq-one, groq-two")
            .withProperty("GEMINI_MODEL","gemini-one")
            .withProperty("GEMINI_MODELS","gemini-one, gemini-two"));
        return new AiProviderRouter(settings,groq,gemini,millis::get);
    }

    @Test void exhaustingBothGroqModelsFallsThroughToGemini() {
        var r=router(true,"GROQ_FIRST");
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,90_000));
        when(groq.completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,60_000));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(groq,List.of(),List.of(),true,deadline()).provider()).isSameAs(gemini);
        verify(groq).complete(anyList(),anyList(),anyLong());
        verify(groq).completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong());
        verify(gemini).complete(anyList(),anyList(),anyLong());
        // Individual cooldown: the next turn need not hit Groq again.
        assertThat(r.call(groq,List.of(),List.of(),true,deadline()).provider()).isSameAs(gemini);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());
    }

    @Test void sameVendorAlternateModelWorksWhenPrivateCrossVendorIsOff() {
        var r=router(false,"GROQ_FIRST");
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,10_000));
        when(groq.completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(groq,List.of(),List.of(),true,deadline()).provider()).isSameAs(groq);
        verifyNoInteractions(gemini);
    }

    @Test void sensitiveDataCanSwitchCompaniesOnlyWithExplicitConsent() {
        var r=router(false,"GROQ_FIRST");
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,0));
        when(groq.completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,0));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline())).isInstanceOf(AiProvider.Failure.class);
        verifyNoInteractions(gemini);
    }

    @Test void geminiOnlyTriesNextGeminiModelWhenFirstIsRateLimited() {
        var r=router(false,"GEMINI_ONLY");
        when(gemini.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,0));
        when(gemini.completeWithModel(eq("gemini-two"),anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(gemini,List.of(),List.of(),true,deadline()).provider()).isSameAs(gemini);
        verifyNoInteractions(groq);
    }

    @Test void autoCanRecoverFromGeminiToGroqWhenConfigured() {
        var r=router(true,"AUTO");
        when(gemini.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,0));
        when(gemini.completeWithModel(eq("gemini-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,0));
        when(groq.complete(anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(gemini,List.of(),List.of(),true,deadline()).provider()).isSameAs(groq);
    }

    @Test void groqFirstCanAlsoRecoverFromGeminiToGroqOnLaterRound() {
        var r=router(true,"GROQ_FIRST");
        when(gemini.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,503,0));
        when(gemini.completeWithModel(eq("gemini-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,503,0));
        when(groq.complete(anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(gemini,List.of(),List.of(),true,deadline()).provider()).isSameAs(groq);
    }

    @Test void invalidGroqKeyMayFailOverToConfiguredGemini() {
        var r=router(true,"GROQ_FIRST");
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(false,401,0));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok);
        assertThat(r.call(groq,List.of(),List.of(),true,deadline()).provider()).isSameAs(gemini);
        verify(groq,never()).completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong());
    }

    @Test void streamingPartialFailureIsBufferedSoGeminiCanRetryCleanly() {
        var r=router(true,"GROQ_FIRST");
        when(groq.completeStreaming(anyList(),anyLong(),any())).thenAnswer(invocation->{
            @SuppressWarnings("unchecked") java.util.function.Consumer<String> cb=invocation.getArgument(2);
            cb.accept("partial");
            throw new AiProvider.Failure(true,429,0);
        });
        when(groq.completeStreamingWithModel(eq("groq-two"),anyList(),anyLong(),any())).thenAnswer(invocation->{
            @SuppressWarnings("unchecked") java.util.function.Consumer<String> cb=invocation.getArgument(3);
            cb.accept("incomplete");
            throw new AiProvider.Failure(true,429,0);
        });
        when(gemini.completeStreaming(anyList(),anyLong(),any())).thenAnswer(invocation->{
            @SuppressWarnings("unchecked") java.util.function.Consumer<String> cb=invocation.getArgument(2);
            cb.accept("Kết quả Gemini");
            return ok;
        });
        var chunks=new ArrayList<String>();
        assertThat(r.call(groq,List.of(),List.of(),true,deadline(),chunks::add).provider()).isSameAs(gemini);
        assertThat(chunks).containsExactly("Kết quả Gemini");
    }

    @Test void allModelsUnavailableStillReturnRateLimitWithoutInfiniteLoop() {
        var r=router(true,"GROQ_FIRST");
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,20_000));
        when(groq.completeWithModel(eq("groq-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,40_000));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,50_000));
        when(gemini.completeWithModel(eq("gemini-two"),anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,60_000));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline()))
            .isInstanceOfSatisfying(AiProvider.Failure.class,e->{
                assertThat(e.statusCode()).isEqualTo(429);
                assertThat(e.retryAfterMillis()).isGreaterThan(0);
            });
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());
    }
}
