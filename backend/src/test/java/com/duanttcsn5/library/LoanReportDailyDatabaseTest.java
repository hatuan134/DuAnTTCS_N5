package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanReportRepository;
import com.duanttcsn5.library.service.LoanReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in integration tests. Use ONLY a dedicated PostgreSQL test database with Flyway V55+. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_07_3_DB_TEST", matches = "true")
@Transactional
class LoanReportDailyDatabaseTest {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired private LoanReportRepository repository;
    @Autowired private LoanReportService service;
    @Autowired private JdbcTemplate jdbc;

    private LoanReportRepository.Activity activity(LocalDate start, LocalDate end) {
        return repository.activity(start, end, start.atStartOfDay(VIETNAM).toOffsetDateTime(),
                end.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
    }

    private long copy(long book) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 2, 50000, 'GOOD')
                RETURNING id
                """, Long.class, book, "C-" + UUID.randomUUID());
    }

    private void borrow(long loanId, long copyId, String timestamp) {
        jdbc.update("INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at) VALUES (?, ?, ?)",
                loanId, copyId, OffsetDateTime.parse(timestamp));
    }

    @Test
    void multipleCopiesMidnightZeroDatesSingleDayAndLongRanges() {
        Long authorCount = jdbc.queryForObject("SELECT COUNT(*) FROM authors", Long.class);
        Long categoryCount = jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class);
        Long shelfCount = jdbc.queryForObject("SELECT COUNT(*) FROM shelves", Long.class);
        assumeTrue(authorCount != null && authorCount > 0 && categoryCount != null && categoryCount > 0
                && shelfCount != null && shelfCount > 0, "Test DB phải có ít nhất một tác giả, thể loại, kệ.");

        LocalDate start = LocalDate.of(2026, 9, 10);
        LocalDate end = LocalDate.of(2026, 9, 12);
        var before = activity(start, end);
        long reader = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, 'Biểu đồ ngày', ?, 'ACTIVE' FROM roles WHERE code='READER' RETURNING id
                """, Long.class, "chart-" + UUID.randomUUID() + "@example.invalid");
        long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S4-07.3 " + UUID.randomUUID());
        long firstLoan = jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, borrowed_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, "CHART-" + UUID.randomUUID(), reader,
                OffsetDateTime.parse("2026-09-10T23:59:59+07:00"));
        borrow(firstLoan, copy(book), "2026-09-10T23:59:59+07:00");
        borrow(firstLoan, copy(book), "2026-09-10T23:59:59+07:00");
        long secondLoan = jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, borrowed_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, "CHART-" + UUID.randomUUID(), reader,
                OffsetDateTime.parse("2026-09-11T00:00:00+07:00"));
        borrow(secondLoan, copy(book), "2026-09-11T00:00:00+07:00");

        var after = activity(start, end);
        assertThat(after.dailyBorrowed()).extracting(LoanReportRepository.DayBorrowCount::date)
                .containsExactly(start, start.plusDays(1), end);
        assertThat(after.dailyBorrowed().get(0).borrowCount() - before.dailyBorrowed().get(0).borrowCount()).isEqualTo(2);
        assertThat(after.dailyBorrowed().get(1).borrowCount() - before.dailyBorrowed().get(1).borrowCount()).isEqualTo(1);
        assertThat(after.dailyBorrowed().get(2).borrowCount() - before.dailyBorrowed().get(2).borrowCount()).isZero();
        assertThat(after.borrowed() - before.borrowed()).isEqualTo(3);
        assertThat(after.dailyBorrowed().stream().mapToLong(LoanReportRepository.DayBorrowCount::borrowCount).sum())
                .isEqualTo(after.borrowed());
        assertThat(activity(start, start).dailyBorrowed()).hasSize(1);
        assertThat(activity(start.minusDays(6), start).dailyBorrowed()).hasSize(7);
        assertThat(activity(start.minusDays(29), start).dailyBorrowed()).hasSize(30);
        assertThat(activity(start.minusMonths(3), end).dailyBorrowed())
                .hasSize((int) java.time.temporal.ChronoUnit.DAYS.between(start.minusMonths(3), end) + 1);
        assertThat(activity(LocalDate.of(1980, 1, 1), LocalDate.of(1980, 1, 3)).dailyBorrowed())
                .allMatch(day -> day.borrowCount() == 0);
    }

    @Test
    void completeReportUnderThreeSecondsWithFiveHundredRecentLoanSlips() {
        LocalDate end = LocalDate.now(VIETNAM);
        LocalDate start = end.minusDays(29);
        Long sampleSize = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT loan_id) FROM loan_items
                WHERE borrowed_at >= ? AND borrowed_at < ?
                """, Long.class, start.atStartOfDay(VIETNAM).toOffsetDateTime(),
                end.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
        assumeTrue(sampleSize != null && sampleSize >= 500,
                "Cần nạp tối thiểu 500 phiếu mượn trong 30 ngày vào TEST DB trước khi đo.");
        service.report(start, end); // Warm up the app and PostgreSQL query cache.
        var report = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> service.report(start, end));
        assertThat(report.dailyLoans()).hasSize(30);
        assertThat(report.dailyLoans().stream().mapToLong(item -> item.borrowCount()).sum())
                .isEqualTo(report.totalBorrowed());
        assertThat(report.topBooks()).hasSizeLessThanOrEqualTo(10);
        assertThat(report.topQueuedBooks()).hasSizeLessThanOrEqualTo(10);
    }
}
