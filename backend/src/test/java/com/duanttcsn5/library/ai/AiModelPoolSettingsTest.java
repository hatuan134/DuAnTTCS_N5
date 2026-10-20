package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class AiModelPoolSettingsTest {
    @Test void orderedModelListsTrimDeduplicateAndRespectLimit() {
        var settings=new AiSettings(new MockEnvironment()
            .withProperty("GROQ_MODELS","openai/gpt-oss-120b, openai/gpt-oss-20b,openai/gpt-oss-120b")
            .withProperty("GEMINI_MODELS","gemini-2.5-flash, gemini-2.5-flash-lite"));
        assertThat(settings.models("GROQ")).containsExactly("openai/gpt-oss-120b","openai/gpt-oss-20b");
        assertThat(settings.models("GEMINI")).containsExactly("gemini-2.5-flash","gemini-2.5-flash-lite");
    }
    @Test void oldSingleModelConfigStillWorks() {
        var settings=new AiSettings(new MockEnvironment().withProperty("GROQ_MODEL","my-model"));
        assertThat(settings.models("GROQ")).containsExactly("my-model");
    }
    @Test void unlimitedAppRateAndTurnLimitsAreDefaults() {
        var unlimited=new AiSettings(new MockEnvironment()
            .withProperty("AI_USER_REQUESTS_PER_MINUTE","0")
            .withProperty("AI_GLOBAL_REQUESTS_PER_MINUTE","0")
            .withProperty("AI_MAX_TURNS_PER_CONVERSATION","0"));
        assertThat(unlimited.userLimit()).isZero();
        assertThat(unlimited.globalLimit()).isZero();
        assertThat(unlimited.turnLimit()).isZero();
        var safe=new AiSettings(new MockEnvironment());
        assertThat(safe.userLimit()).isZero();
        assertThat(safe.globalLimit()).isZero();
        assertThat(safe.turnLimit()).isZero();
    }
    @Test void privateDataNeedsBothCrossVendorAndPrivateOptIn() {
        var no=new AiSettings(new MockEnvironment()
            .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK","true")
            .withProperty("AI_ALLOW_PRIVATE_CONTEXT","false"));
        assertThat(no.privateProviderSwitchAllowed()).isFalse();
        var yes=new AiSettings(new MockEnvironment()
            .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK","true")
            .withProperty("AI_ALLOW_PRIVATE_CONTEXT","true"));
        assertThat(yes.privateProviderSwitchAllowed()).isTrue();
    }
}
