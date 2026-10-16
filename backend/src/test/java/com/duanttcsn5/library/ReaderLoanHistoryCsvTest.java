package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.reader.ReaderLoanHistoryResponse;
import com.duanttcsn5.library.dto.reader.ReaderProfileResponse;
import com.duanttcsn5.library.service.ReaderLoanHistoryCsv;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReaderLoanHistoryCsvTest {
    private final ReaderProfileResponse profile = new ReaderProfileResponse(20L, "Nguyễn An", null,
            null, null, "ACTIVE", "BD000020", null, null, null, null, null, null, null);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-09T16:05:06.123Z");

    @Test
    void emptyHistoryHasBomAndOnlyHeaderWithRecognizableFilename() {
        var result = ReaderLoanHistoryCsv.create(new ReaderLoanHistoryResponse(profile, 0, 0, 0, List.of()), now);
        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF\"Mã Bạn đọc\"").endsWith("\r\n");
        assertThat(csv.lines().count()).isEqualTo(1);
        assertThat(result.rowCount()).isZero();
        assertThat(result.filename()).isEqualTo("lich-su-muon-tra_BD000020_20_20261009_230506_123.csv");
    }

    @Test
    void multiCopyLoansPreserveEscapedTitlesDatesStatusAndLateFlags() {
        var borrowed = OffsetDateTime.parse("2026-10-01T02:00:00Z");
        var due = OffsetDateTime.parse("2026-10-02T00:00:00+07:00");
        var returned = OffsetDateTime.parse("2026-10-03T09:00:00+07:00");
        var first = new ReaderLoanHistoryResponse.Item(11L, "Lập trình, \"Java\"\nTiếng Việt", "00001234", borrowed, due, null, "BORROWED", false);
        var second = new ReaderLoanHistoryResponse.Item(12L, "Sách đã trả", "BC-2", borrowed, due, returned, "RETURNED", true);
        var loan = new ReaderLoanHistoryResponse.Loan(2L, "PM-2", borrowed, "PARTIALLY_RETURNED", true, List.of(first, second));
        var result = ReaderLoanHistoryCsv.create(new ReaderLoanHistoryResponse(profile, 1, 1, 1, List.of(loan)), now);
        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(csv).contains("\"Lập trình, \"\"Java\"\"\nTiếng Việt\"", "\"00001234\"", "\"2026-10-01 09:00:00\"", "\"2026-10-02\"");
        assertThat(csv).contains("\"2026-10-03 09:00:00\"", "\"Đã trả một phần\",\"Có\",\"Không\"", "\"Đã trả một phần\",\"Có\",\"Có\"");
    }

    @Test
    void formulasAreNeutralizedAndLegacyLoanStillAppears() {
        var item = new ReaderLoanHistoryResponse.Item(1L, "  =1+1", "@code", null, null, null, "BORROWED", false);
        var loan = new ReaderLoanHistoryResponse.Loan(2L, "PM-2", now, "BORROWED", false, List.of(item));
        var legacy = new ReaderLoanHistoryResponse.Loan(1L, "PM-OLD", now.minusDays(1), "EMPTY", false, List.of());
        var result = ReaderLoanHistoryCsv.create(new ReaderLoanHistoryResponse(profile, 1, 2, 0, List.of(loan, legacy)), now);
        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'  =1+1\"", "\"'@code\"", "PM-OLD", "Chưa có bản sao");
        assertThat(csv.indexOf("PM-2")).isLessThan(csv.indexOf("PM-OLD"));
        assertThat(result.rowCount()).isEqualTo(2);
    }
}
