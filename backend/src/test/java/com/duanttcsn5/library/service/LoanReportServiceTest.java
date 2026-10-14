package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.LoanReportResponse.TopBorrowedBook;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoanReportServiceTest {
    private final LoanReportRepository repository = mock(LoanReportRepository.class);
    private final LoanReportService service = new LoanReportService(repository,
            Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC));

    private void stubCounts(long borrowed, long returned, long open, long overdue) {
        when(repository.activity(any(), any())).thenReturn(new LoanReportRepository.Activity(borrowed, returned));
        when(repository.openAndOverdue(any(), any())).thenReturn(new LoanReportRepository.OpenAndOverdue(open, overdue));
        when(repository.topBooks(any(), any())).thenReturn(List.of());
    }

    @Test
    void defaultsToThirtyInclusiveVietnamCalendarDays() {
        stubCounts(500, 420, 80, 20);
        when(repository.topBooks(any(), any())).thenReturn(List.of(
                new TopBorrowedBook(4, "Cơ sở dữ liệu", 65, 3)));

        var report = service.report(null, null);

        assertThat(report.startDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(report.endDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(report.totalBorrowed()).isEqualTo(500);
        assertThat(report.totalReturned()).isEqualTo(420);
        assertThat(report.openLoans()).isEqualTo(80);
        assertThat(report.overdueLoans()).isEqualTo(20);
        assertThat(report.overdueRate()).isEqualTo(25.0);
        assertThat(report.topBooks()).containsExactly(new TopBorrowedBook(4, "Cơ sở dữ liệu", 65, 3));
        verify(repository).activity(OffsetDateTime.parse("2026-09-15T00:00:00+07:00"),
                OffsetDateTime.parse("2026-10-15T00:00:00+07:00"));
        verify(repository).openAndOverdue(OffsetDateTime.parse("2026-10-14T00:00:00+07:00"),
                OffsetDateTime.parse("2026-10-15T00:00:00+07:00"));
    }

    @Test
    void acceptsSingleDayIncludingNextMidnightAsExclusiveEnd() {
        stubCounts(2, 3, 1, 0);
        var report = service.report(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        assertThat(report.overdueRate()).isZero();
        verify(repository).activity(OffsetDateTime.parse("2026-10-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-10-02T00:00:00+07:00"));
    }

    @Test
    void rejectsStartAfterEndWithoutDatabaseQueries() {
        assertThatThrownBy(() -> service.report(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Ngày bắt đầu");
        verifyNoInteractions(repository);
    }

    @Test
    void zeroDenominatorUsesZeroPercentRatherThanNaN() {
        stubCounts(0, 0, 0, 0);
        var report = service.report(null, null);
        assertThat(report.overdueRate()).isZero();
        assertThat(report.topBooks()).isEmpty();
    }

    @Test
    void hundredPercentOverdueIsRepresentedCorrectly() {
        stubCounts(1, 0, 1, 1);
        assertThat(service.report(null, null).overdueRate()).isEqualTo(100.0);
    }

    @Test
    void defaultRangeStartsInVietnamNotUtcNearMidnight() {
        var nearMidnight = new LoanReportService(repository,
                Clock.fixed(Instant.parse("2026-10-13T17:15:00Z"), ZoneOffset.UTC));
        stubCounts(0, 0, 0, 0);
        assertThat(nearMidnight.report(null, null).endDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }
}
