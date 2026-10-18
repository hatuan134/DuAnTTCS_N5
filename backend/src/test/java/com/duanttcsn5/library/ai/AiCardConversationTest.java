package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.dto.librarycard.LibraryCardResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Tool results come from a role-scoped service; the provider never receives card-holder PII. */
class AiCardConversationTest {
    AiToolsSecurityTest fixture=new AiToolsSecurityTest();
    JsonMapper json=JsonMapper.builder().build();
    @AfterEach void logout() {fixture.clear();}

    private LibraryCardResponse card(String status,String name,String number) {
        LibraryCardResponse c=mock(LibraryCardResponse.class);
        when(c.status()).thenReturn(status);
        when(c.readerName()).thenReturn(name);
        when(c.cardNumber()).thenReturn(number);
        return c;
    }
    private void seed() {
        // Prepare nested mocks before opening the outer Mockito stubbing.
        // Otherwise card() starts another when(...) while thenReturn(...) is unfinished.
        var active = card("ACTIVE", "Độc giả A", "A001");
        var locked = card("LOCKED", "Nguyễn A", "X007");
        var expired = card("EXPIRED", "Độc giả B", "B003");
        when(fixture.cards.getIssuedCards()).thenReturn(List.of(active, locked, expired));
    }
    @Test void roleScopedCardToolsExposeNoNamesToModelAndReaderCannotCall() {
        AiToolsSecurityTest.login("READER",11);
        assertThatThrownBy(()->fixture.tools.execute("searchAuthorizedLibraryCards",json.readTree("{}")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(fixture.cards);
        AiToolsSecurityTest.login("ADMIN",13);seed();
        var totals=fixture.tools.execute("getLibraryCardStatusSummary",json.readTree("{}"));
        assertThat(AiNaturalAnswer.compose("Bao nhiêu thẻ bị khóa?",List.of(totals.block())))
            .contains("1 thẻ đang bị khóa");
        var owners=fixture.tools.execute("searchAuthorizedLibraryCards",json.readTree("{\"status\":\"LOCKED\"}"));
        assertThat(owners.block().items()).hasSize(1);
        assertThat(AiNaturalAnswer.compose("Ai sở hữu những thẻ đó?",List.of(owners.block())))
            .contains("Nguyễn A","X007").doesNotContain("Độc giả A");
        assertThat(owners.providerData().toString()).doesNotContain("Nguyễn A","X007","Độc giả A");
    }
    @Test void conversationRemembersStatusAndEnforcesItOnFollowUp() {
        AiToolsSecurityTest.login("ADMIN",13);seed();
        var router=mock(AiProviderRouter.class);var groq=mock(GroqProvider.class);
        when(groq.name()).thenReturn("GROQ");when(router.select(anyString())).thenReturn(groq);
        String statusCall="{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"getLibraryCardStatusSummary\",\"arguments\":\"{}\"}}]}";
        String ownerCall="{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c2\",\"type\":\"function\",\"function\":{\"name\":\"searchAuthorizedLibraryCards\",\"arguments\":\"{}\"}}]}";
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong())).thenReturn(
            new AiProviderRouter.Answer(groq,json.readTree(statusCall)),
            new AiProviderRouter.Answer(groq,json.readTree("{\"role\":\"assistant\",\"content\":\"OK\"}")),
            new AiProviderRouter.Answer(groq,json.readTree(statusCall)),
            new AiProviderRouter.Answer(groq,json.readTree("{\"role\":\"assistant\",\"content\":\"OK\"}")),
            new AiProviderRouter.Answer(groq,json.readTree(ownerCall)),
            new AiProviderRouter.Answer(groq,json.readTree("{\"role\":\"assistant\",\"content\":\"OK\"}")));
        var settings=new AiSettings(new MockEnvironment().withProperty("AI_ENABLED","true"));
        var svc=new AiChatService(settings,router,fixture.tools,fixture.access,fixture.navigation,
            new AiSessionStore(),new AiRateLimiter(settings),json);
        var a=svc.chat(new ChatRequest("Hệ thống có bao nhiêu thẻ thư viện?",null,"/library-cards"),"test-client");
        var b=svc.chat(new ChatRequest("Trong số đó có bao nhiêu thẻ bị khóa?",a.conversationId(),"/library-cards"),"test-client");
        assertThat(b.message()).contains("1 thẻ đang bị khóa");
        var c=svc.chat(new ChatRequest("Những thẻ đó thuộc về ai?",a.conversationId(),"/library-cards"),"test-client");
        assertThat(c.message()).contains("Nguyễn A","X007").doesNotContain("Độc giả A");
        // Verify the second turn really received a status-focused tool definition.
        verify(router,atLeast(3)).call(any(),anyList(),anyList(),eq(true),anyLong());
    }
}
