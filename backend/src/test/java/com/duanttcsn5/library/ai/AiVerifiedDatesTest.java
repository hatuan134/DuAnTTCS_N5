package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class AiVerifiedDatesTest {
    @Test void tet2027UsesAuditedDateNotModelGuess() {
        var text=AiVerifiedDates.answer("Còn bao lâu nữa đến Tết?",LocalDate.of(2026,10,18)).orElseThrow();
        assertThat(text).contains("06/02/2027","111 ngày");
        assertThat(text).doesNotContain("22/01/2027");
    }
    @Test void VietnameseWeekdayIsCalculatedNotGuessed() {
        assertThat(AiVerifiedDates.answer("Hôm nay là thứ mấy?",LocalDate.of(2026,10,18)).orElseThrow())
            .contains("Chủ nhật","18/10/2026");
    }
    @Test void unknownLunarYearDoesNotHallucinate() {
        assertThat(AiVerifiedDates.answer("Tết 2028 ngày nào?",LocalDate.of(2026,10,18)).orElseThrow())
            .contains("chưa có lịch âm đã kiểm chứng");
    }
    @Test void unrelatedLibraryRequestStillGoesToLibraryPipeline() {
        assertThat(AiVerifiedDates.answer("Tìm sách Java",LocalDate.of(2026,10,18))).isEmpty();
    }
}
