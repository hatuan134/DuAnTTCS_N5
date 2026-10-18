package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

class AiLiveDataAnswerTest {
    @Test void calendarQuestionsPreferReadingActualData() {
        assertThat(AiChatService.isCalendarDataRequest("Lịch đóng cửa của LIBRA như thế nào?")).isTrue();
        assertThat(AiChatService.isCalendarDataRequest("Giờ mở cửa thư viện hiện tại là gì?")).isTrue();
        assertThat(AiChatService.isCalendarDataRequest("Mở trang lịch thư viện")).isFalse();
    }
    @Test void thresholdQuestionsPreferReadingActualData() {
        assertThat(AiChatService.isDebtThresholdDataRequest("Ngưỡng nợ phí của hệ thống hiện tại là bao nhiêu?")).isTrue();
        assertThat(AiChatService.isDebtThresholdDataRequest("Mở trang ngưỡng nợ phí")).isFalse();
    }
    @Test void cardsCountRoutesToDashboardButPersonalCardDoesNot() {
        assertThat(AiChatService.isDashboardRequest("Hệ thống hiện tại có bao nhiêu thẻ thư viện?")).isTrue();
        assertThat(AiChatService.isDashboardRequest("Thẻ của tôi còn hạn không?")).isFalse();
    }
    @Test void renderCalendarValuesInTheFirstTextAnswer() {
        var rows=List.<Map<String,Object>>of(
            Map.of("kind","weekly","title","Chủ Nhật","open",false),
            Map.of("kind","weekly","title","Thứ Hai","open",true,"openTime","08:00","closeTime","17:00"),
            Map.of("kind","special","closedDate","2026-12-31","reason","Bảo trì"));
        var block=new Block("libraryCalendar","Lịch thư viện",rows,
            Map.of("year",2026,"totalClosedDates",1,"upcomingClosedDates",1),List.of());
        var answer=AiNaturalAnswer.compose("Lịch đóng cửa?",List.of(block));
        assertThat(answer).contains("Chủ Nhật","Thứ Hai","08:00","17:00","31", "Bảo trì");
    }
    @Test void renderConfiguredThresholdWithoutInventingDebt() {
        var block=new Block("settings","Ngưỡng nợ phí",List.of(),Map.of("thresholdVnd",150000L),List.of());
        assertThat(AiNaturalAnswer.compose("Ngưỡng nợ phí?",List.of(block)))
            .contains("150.000", "đề xuất khóa thẻ").doesNotContain("đã khóa thẻ");
    }
}
