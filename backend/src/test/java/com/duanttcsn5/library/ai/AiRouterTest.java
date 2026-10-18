package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AiRouterTest {
    GroqProvider groq=mock(GroqProvider.class);
    GeminiProvider gemini=mock(GeminiProvider.class);
    AiProviderRouter router(String mode,boolean fallback) {
        when(groq.name()).thenReturn("GROQ");
        when(gemini.name()).thenReturn("GEMINI");
        when(groq.configured()).thenReturn(true);when(gemini.configured()).thenReturn(true);
        return new AiProviderRouter(new AiSettings(new MockEnvironment().withProperty("AI_PROVIDER_MODE",mode).withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK",String.valueOf(fallback))),groq,gemini);
    }
    @Test void autoRoutesAnalysisAndConversation() {
        var r=router("AUTO",false);
        assertThat(r.select("Xin chào")).isSameAs(groq);assertThat(r.select("Phân tích báo cáo")).isSameAs(gemini);
    }
    @Test void explicitModesDoNotSwitch() {
        assertThat(router("GROQ_ONLY",true).select("báo cáo")).isSameAs(groq);
        assertThat(router("GEMINI_ONLY",true).select("xin chào")).isSameAs(gemini);
    }
    @Test void fallbackIsBoundedAndOptIn() {
        var r=router("AUTO",true);long deadline=System.nanoTime()+1000000000L;
        when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true));
        when(gemini.complete(anyList(),anyList(),anyLong())).thenReturn(JsonMapper.builder().build().readTree("{\"content\":\"ok\"}"));
        assertThat(r.call(groq,List.of(),List.of(),false,deadline).provider()).isSameAs(gemini);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());verify(gemini,times(1)).complete(anyList(),anyList(),anyLong());
    }
    @Test void sensitiveDataNeverCrossesProvider() {
        var r=router("AUTO",true);when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),true,System.nanoTime()+1000000000L)).isInstanceOf(AiProvider.Failure.class);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());verify(gemini,never()).complete(anyList(),anyList(),anyLong());
    }
    @Test void singleConfiguredModelGetsOneAttemptPerTurn() {
        var r=router("AUTO",false);when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(true));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),false,System.nanoTime()+1000000000L)).isInstanceOf(AiProvider.Failure.class);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());verify(gemini,never()).complete(anyList(),anyList(),anyLong());
    }
    @Test void nonRetryableDoesNotRetry() {
        var r=router("AUTO",true);when(groq.complete(anyList(),anyList(),anyLong())).thenThrow(new AiProvider.Failure(false));
        assertThatThrownBy(()->r.call(groq,List.of(),List.of(),false,System.nanoTime()+1000000000L)).isInstanceOf(AiProvider.Failure.class);
        verify(groq,times(1)).complete(anyList(),anyList(),anyLong());verify(gemini,never()).complete(anyList(),anyList(),anyLong());
    }
}
