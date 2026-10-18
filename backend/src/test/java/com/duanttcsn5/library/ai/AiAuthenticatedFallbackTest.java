package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** The security decision concerns the actual payload, NOT whether someone logged in. */
class AiAuthenticatedFallbackTest {
    AiToolsSecurityTest fixture=new AiToolsSecurityTest();
    AiProviderRouter router=mock(AiProviderRouter.class);
    GroqProvider groq=mock(GroqProvider.class);
    JsonMapper json=JsonMapper.builder().build();
    AiSettings settings=new AiSettings(new MockEnvironment()
        .withProperty("AI_ENABLED","true")
        .withProperty("AI_PROVIDER_MODE","GROQ_FIRST")
        .withProperty("AI_ALLOW_PRIVATE_CONTEXT","false")
        .withProperty("AI_ALLOW_CROSS_PROVIDER_FALLBACK","true"));
    AiChatService service=new AiChatService(settings,router,fixture.tools,fixture.access,
        fixture.navigation,new AiSessionStore(),new AiRateLimiter(settings),json);

    @BeforeEach void setUp() {
        when(router.select(anyString())).thenReturn(groq);
        when(groq.name()).thenReturn("GROQ");
    }
    @AfterEach void cleanup() { fixture.clear(); }

    private AiProviderRouter.Answer model(String body) {
        return new AiProviderRouter.Answer(groq,json.readTree(body));
    }
    private String toolCall(String name,String arguments) {
        return "{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\""
            +name+"\",\"arguments\":"+json.writeValueAsString(arguments)+"}}]}";
    }

    @Test void adminPublicCatalogQuestionIsFallbackEligibleButNeverForwardsPrivateHistory() {
        AiToolsSecurityTest.login("ADMIN",13);
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong())).thenReturn(
            model("{\"role\":\"assistant\",\"content\":\"Chưa kiểm tra được dữ liệu.\"}"),
            model(toolCall("searchBooks","{\"query\":\"\",\"publicationYear\":2025}")),
            model("{\"role\":\"assistant\",\"content\":\"Chưa có sách tương ứng.\"}"));
        when(fixture.catalog.searchPublicBooks("",null,2025,false,0,6,"publicationYear"))
            .thenReturn(new com.duanttcsn5.library.dto.book.PublicCatalogPageResponse(
                List.of(),0,6,0,0,true,true,"publicationYear"));
        var privateTurn=service.chat(new ChatRequest("Danh sách hồ sơ bạn đọc nội bộ",null,"/readers"),"local");
        service.chat(new ChatRequest("Tìm cho tôi sách xuất bản năm 2025.",privateTurn.conversationId(),"/users"),"local");

        @SuppressWarnings("unchecked") var messages=(List<Map<String,Object>>) mockingDetails(router).getInvocations().stream()
            .filter(i->i.getMethod().getName().equals("call")).skip(1).findFirst().orElseThrow().getArgument(1);
        // Mockito captures the SAME mutable list; after the public search tool executes,
        // the assistant tool call and tool result are appended in-place (2 -> 4).
        // The security contract is that the initial prompt and ALL later tool messages
        // contain only this public query, without any private turn or admin page context.
        assertThat(messages).hasSize(4);
        assertThat(messages.subList(0, 2)).extracting(row -> row.get("role"))
            .containsExactly("system", "user");
        assertThat(messages.get(1).get("content")).isEqualTo("Tìm cho tôi sách xuất bản năm 2025.");
        assertThat(messages.toString()).doesNotContain("hồ sơ bạn đọc nội bộ","/users","Vai trò xác thực: ADMIN");
        assertThat(messages.toString()).contains("DANH MỤC SÁCH CÔNG KHAI");
        verify(router,times(2)).call(eq(groq),anyList(),anyList(),eq(false),anyLong());
        verify(router).call(eq(groq),anyList(),anyList(),eq(true),anyLong());
        verifyNoInteractions(fixture.read,fixture.loans);
    }

    @Test void privateAuthenticatedQuestionStillDisablesFallback() {
        AiToolsSecurityTest.login("READER",11);
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong()))
            .thenReturn(model("{\"role\":\"assistant\",\"content\":\"Vui lòng xem phiếu mượn của mình.\"}"));
        service.chat(new ChatRequest("Phiếu mượn của tôi còn hạn không?",null,"/my-borrowed-books"),"local");
        verify(router).call(eq(groq),anyList(),anyList(),eq(true),anyLong());
    }

    @Test void fabricatedPrivateToolCallOnPublicLaneIsRejectedAndDisablesFallback() {
        AiToolsSecurityTest.login("ADMIN",13);
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong())).thenReturn(
            model(toolCall("searchAuthorizedReaders","{}")),
            model("{\"role\":\"assistant\",\"content\":\"Không có dữ liệu.\"}"));
        var response=service.chat(new ChatRequest("Tìm sách xuất bản năm 2025",null,"/readers"),"local");
        assertThat(response.blocks()).extracting(Block::title).contains("Không được cấp quyền");
        verifyNoInteractions(fixture.read,fixture.loans);
        // Model-generated rejected private tool arguments MUST NOT cross providers.
        verify(router).call(eq(groq),anyList(),anyList(),eq(true),anyLong());
    }
}
