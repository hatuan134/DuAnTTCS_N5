package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.function.Consumer;
import static com.duanttcsn5.library.ai.AiTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiScopeServiceTest {
    AiToolsSecurityTest fixture=new AiToolsSecurityTest();
    AiProviderRouter router=mock(AiProviderRouter.class);
    AiHistoryStore history=mock(AiHistoryStore.class);
    AiSessionStore sessions=new AiSessionStore();
    AiSettings settings=new AiSettings(new MockEnvironment().withProperty("AI_ENABLED","true"));
    AiChatService service=new AiChatService(settings,router,fixture.tools,fixture.access,
        fixture.navigation,sessions,new AiRateLimiter(settings),JsonMapper.builder().build(),history);
    @AfterEach void logout() { fixture.clear(); }

    @ParameterizedTest @ValueSource(strings={"PUBLIC","READER","LIBRARIAN","LIBRARY_MANAGER","ADMIN"})
    void bothModesBlockBeforeRouterForEveryRoleAndKeepHistory(String role) {
        AiToolsSecurityTest.login(role,11);
        var first=service.chat(new ChatRequest("Tết ngày nào?",null,"/catalog"),"local");
        List<String> tokens=new ArrayList<>();
        var next=service.chat(new ChatRequest("Viết code Python",first.conversationId(),"/reports/loans"),"local",tokens::add);
        assertThat(first.provider()).isEqualTo("LIBRA_SCOPE");
        assertThat(next.provider()).isEqualTo("LIBRA_SCOPE");
        assertThat(next.conversationId()).isEqualTo(first.conversationId());
        assertThat(next.turnsRemaining()).isEqualTo(Integer.MAX_VALUE);
        assertThat(tokens).isEmpty();
        verifyNoInteractions(router);
        if(!role.equals("PUBLIC")) verify(history,times(2)).save(anyString(),anyString(),any());
        else verifyNoInteractions(history);
    }
    @Test void fortyTurnsIsNoLongerACapAndSessionContextRemainsBounded() {
        AiToolsSecurityTest.login("PUBLIC",0);
        String id=null;
        for(int i=0;i<60;i++) id=service.chat(new ChatRequest("Tết ngày nào?",id,"/"),"local").conversationId();
        var session=sessions.acquire(id,fixture.access.owner("local"));
        try {
            assertThat(session.turns).isEqualTo(60);
            assertThat(session.history).hasSize(12);
        } finally { session.lock.unlock(); }
        verifyNoInteractions(router);
    }
    @Test void allowedGreetingStillUsesStreamingProvider() {
        AiToolsSecurityTest.login("PUBLIC",0);
        var groq=mock(GroqProvider.class);
        when(groq.name()).thenReturn("GROQ");
        when(router.select(anyString())).thenReturn(groq);
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong(),any())).thenAnswer(inv->{
            Consumer<String> tokens=inv.getArgument(5); tokens.accept("Chào bạn");
            return new AiProviderRouter.Answer(groq,JsonMapper.builder().build().readTree("{\"content\":\"Chào bạn\"}"));
        });
        List<String> tokens=new ArrayList<>();
        var result=service.chat(new ChatRequest("Xin chào",null,"/"),"local",tokens::add);
        assertThat(result.provider()).isEqualTo("GROQ");
        assertThat(tokens).containsExactly("Chào bạn");
    }
}
