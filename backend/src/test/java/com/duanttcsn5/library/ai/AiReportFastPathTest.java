package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** AI-first chooses the reporting tool; numbers remain server-verified. */
class AiReportFastPathTest {
    AiToolsSecurityTest fixture=new AiToolsSecurityTest();
    AiProviderRouter router=mock(AiProviderRouter.class);
    AiSettings settings=new AiSettings(new MockEnvironment().withProperty("AI_ENABLED","true"));
    AiChatService service=new AiChatService(settings,router,fixture.tools,fixture.access,fixture.navigation,
        new AiSessionStore(),new AiRateLimiter(settings),JsonMapper.builder().build());
    @AfterEach void cleanup(){ fixture.clear(); }

    @Test void explicitMonthPeakIsComputedFromVerifiedReportWithoutCallingAI(){
        AiToolsSecurityTest.login("ADMIN",13);
        var today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var start=today.withDayOfMonth(1);
        when(fixture.reports.report(eq(start),eq(today))).thenReturn(
            new com.duanttcsn5.library.dto.report.LoanReportResponse(start,today,25,2,8,2,25,
                List.of(),List.of(new com.duanttcsn5.library.dto.report.LoanReportResponse.DailyLoanCount(start,25)),
                OffsetDateTime.now(),List.of()));
        String question=String.format("Trong tháng %d/%d, ngày nào có lượt mượn nhiều nhất? Hiển thị biểu đồ và bảng số liệu theo ngày.",
            today.getMonthValue(),today.getYear());
        var provider=mock(GroqProvider.class);
        when(router.select(anyString())).thenReturn(provider);
        when(provider.name()).thenReturn("GROQ");
        var json=JsonMapper.builder().build();
        var call="{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"getAuthorizedStatistics\",\"arguments\":\"{}\"}}]}";
        when(router.call(any(),anyList(),anyList(),anyBoolean(),anyLong())).thenReturn(
            new AiProviderRouter.Answer(provider,json.readTree(call)));
        var answer=service.chat(new ChatRequest(question,null,"/reports/loans"),"127.0.0.1");
        assertThat(answer.message()).contains("25","Ngày có lượt mượn cao nhất", "Xem biểu đồ và số liệu chi tiết");
        assertThat(answer.blocks()).extracting(Block::type).containsExactly("statistics");
        verify(router).select(anyString());
        verify(router).call(any(),anyList(),anyList(),anyBoolean(),anyLong());
    }
    @Test void readerCannotUseAdministrativeReportFastPath(){
        AiToolsSecurityTest.login("READER",11);
        // The protected report function is only routed for authorized roles.
        // An unauthorized reader instead follows the normal guarded provider path.
        when(router.select(anyString())).thenThrow(new AiProvider.Failure(false,429,0));
        assertThatThrownBy(()->service.chat(new ChatRequest("Thống kê tình hình mượn trả trong tháng này",null,"/reports/loans"),"127.0.0.1"))
            .isInstanceOf(com.duanttcsn5.library.exception.ApiException.class);
        verifyNoInteractions(fixture.reports);
    }
}
