package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No external API calls or database needed. */
class AiGroqFirstRouterTest {
    GroqProvider groq=mock(GroqProvider.class);
    GeminiProvider gemini=mock(GeminiProvider.class);
    AtomicLong clock=new AtomicLong(1_000_000L);
    AiProviderRouter router(boolean optIn) {
        when(groq.name()).thenReturn("GROQ");
        when(gemini.name()).thenReturn("GEMINI");
        when(groq.configured()).thenReturn(true);
        when(gemini.configured()).thenReturn(true);
        var settings=new AiSettings(new MockEnvironment()
            .withProperty("AI_PROVIDER_MODE","GROQ_FIRST")
            .withProperty("AI_GROQ_COOLDOWN_SECONDS","60")
            .withProperty("AI_GROQ_FAILURE_THRESHOLD","2")
            .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK",String.valueOf(optIn)));
        return new AiProviderRouter(settings,groq,gemini,clock::get);
    }
    private long deadline() {return System.nanoTime()+TimeUnit.SECONDS.toNanos(3);}
    private tools.jackson.databind.JsonNode ok() {return JsonMapper.builder().build().readTree("{\"role\":\"assistant\",\"content\":\"ok\"}");}

    @Test void groqRemainsPrimaryEvenForReports() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        assertThat(r.select("Thống kê mượn trả tháng này")).isSameAs(groq);
        assertThat(r.call(r.select("báo cáo"),List.of(),List.of(),false,deadline()).provider()).isSameAs(groq);
        verifyNoInteractions(gemini);
    }
    @Test void quotaErrorTriggersFallbackAndCooldownThenRecovery() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,30_000L)).thenReturn(ok());
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        assertThat(r.call(groq,List.of(),List.of(),false,deadline()).provider()).isSameAs(gemini);
        assertThat(r.call(groq,List.of(),List.of(),false,deadline()).provider()).isSameAs(gemini);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());
        clock.addAndGet(60_001L);
        assertThat(r.call(groq,List.of(),List.of(),false,deadline()).provider()).isSameAs(groq);
        assertThat(r.call(groq,List.of(),List.of(),false,deadline()).provider()).isSameAs(groq);
        verify(groq,times(3)).complete(anyList(),anyList(),anyLong());
    }
    @Test void cooldownWithoutAuthorizedFallbackPreservesQuotaCauseAndRetryTime() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,30_000L));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline()))
            .isInstanceOfSatisfying(AiProvider.Failure.class,failure ->
                assertThat(failure.statusCode()).isEqualTo(429));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline()))
            .isInstanceOfSatisfying(AiProvider.Failure.class,failure -> {
                assertThat(failure.statusCode()).isEqualTo(429);
                assertThat(failure.retryAfterMillis()).isGreaterThan(0L);
                assertThat(failure.retryAfterMillis()).isLessThanOrEqualTo(60_000L);
            });
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());
        verifyNoInteractions(gemini);
    }
    @Test void twoTransientFailuresOpenCircuit() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,503,0));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        r.call(groq,List.of(),List.of(),false,deadline());
        r.call(groq,List.of(),List.of(),false,deadline());
        r.call(groq,List.of(),List.of(),false,deadline());
        verify(groq,times(2)).complete(anyList(),anyList(),anyLong());
        verify(gemini,times(3)).complete(anyList(),anyList(),anyLong());
    }
    @Test void privateOrUnconsentedRequestNeverSwitchesVendors() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true,429,0));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline())).isInstanceOf(AiProvider.Failure.class);
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,deadline())).isInstanceOf(AiProvider.Failure.class);
        verifyNoInteractions(gemini);
        var r2=router(false);
        assertThatThrownBy(()->r2.call(groq,List.of(),List.of(),false,deadline())).isInstanceOf(AiProvider.Failure.class);
        verifyNoInteractions(gemini);
    }
    @Test void failedGeminiCanTryAvailableGroqInNextRound() {
        var r=router(true);
        when(gemini.complete(anyList(),anyList(),anyLong()))
            .thenThrow(new AiProvider.Failure(true,429,0));
        when(groq.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        assertThat(r.call(gemini,List.of(),List.of(),false,deadline()).provider()).isSameAs(groq);
    }
    @Test void badGroqKeyFallsBackToConfiguredGemini() {
        var r=router(true);
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(false,401,0));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        assertThat(r.call(groq,List.of(),List.of(),false,deadline()).provider()).isSameAs(gemini);
    }
    @Test void partialProviderStreamIsBufferedUntilSuccessfulModel() {
        var r=router(true);
        when(groq.completeStreaming(anyList(),anyLong(),any())).thenAnswer(invocation->{
            @SuppressWarnings("unchecked") java.util.function.Consumer<String> callback=invocation.getArgument(2);
            callback.accept("partial");
            throw new AiProvider.Failure(true,429,0);
        });
        when(gemini.completeStreaming(anyList(),anyLong(),any())).thenAnswer(invocation->{
            @SuppressWarnings("unchecked") java.util.function.Consumer<String> callback=invocation.getArgument(2);
            callback.accept("success");
            return ok();
        });
        var chunks=new ArrayList<String>();
        assertThat(r.call(groq,List.of(),List.of(),false,deadline(),chunks::add).provider()).isSameAs(gemini);
        assertThat(chunks).containsExactly("success");
    }
    @Test void aGroqFirstMissingKeyMayFallbackForPublicOnly() {
        var settings=new AiSettings(new MockEnvironment()
            .withProperty("AI_PROVIDER_MODE","GROQ_FIRST")
            .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK","true"));
        when(gemini.configured()).thenReturn(true);
        var r=new AiProviderRouter(settings,groq,gemini,clock::get);
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(ok());
        assertThat(r.call(r.select("Xin chào"),List.of(),List.of(),false,deadline()).provider()).isSameAs(gemini);
        assertThatThrownBy(()->r.call(r.select("Xin chào"),List.of(),List.of(),true,deadline()))
            .isInstanceOf(AiProvider.Failure.class);
    }
}
