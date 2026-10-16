package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanReportRepository;
import com.duanttcsn5.library.service.LoanReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Opt-in database tests using the project's PostgreSQL 15 database and Flyway schema.
 * Run only on a DEDICATED test database. Fixture rows roll back with the test.
 * S4_07_2_DB_TEST=true enables these tests; no extra dependencies.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_07_2_DB_TEST", matches = "true")
@Transactional
class LoanReportQueueDatabaseTest {
    @Autowired private LoanReportRepository repository;
    @Autowired private LoanReportService service;
    @Autowired private JdbcTemplate jdbc;

    private long reader() {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Queue test", "queue-" + UUID.randomUUID() + "@example.invalid");
    }

    private long book() {
        return jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "Queue title " + UUID.randomUUID());
    }

    private long reserve(long book, long reader, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '1 day') RETURNING id
                """, Long.class, book, reader, status);
    }

    private void copy(long book, String status) {
        jdbc.update("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), ?, CURRENT_DATE - 2, 50000, 'GOOD')
                """, book, "Q-" + UUID.randomUUID(), status);
    }

    private OffsetDateTime snapshot() { return OffsetDateTime.now(); }

    @Test
    void filtersStatusCountsOrdersTiesStockAndUpdatesAfterCancellation() {
        long first = book(), second = book(), empty = book();
        long reader = reader();
        reserve(first, reader, "PENDING");
        reserve(first, reader, "PENDING"); // Count orders, not DISTINCT readers.
        reserve(first, reader, "READY_FOR_PICKUP");
        reserve(first, reader, "FULFILLED");
        reserve(first, reader, "CANCELLED");
        reserve(first, reader, "EXPIRED");
        long secondPending = reserve(second, reader, "PENDING");
        reserve(second, reader, "PENDING");
        reserve(empty, reader, "EXPIRED");
        jdbc.update("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
                VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP + INTERVAL '2 days')
                """, empty, reader);
        copy(first, "AVAILABLE");
        copy(first, "REPAIR");
        copy(first, "REMOVED");

        var result = repository.topQueuedBooks(snapshot());
        var fst = result.stream().filter(x -> x.bookId() == first).findFirst().orElseThrow();
        var snd = result.stream().filter(x -> x.bookId() == second).findFirst().orElseThrow();
        assertThat(fst.waitCount()).isEqualTo(2);
        assertThat(fst.stockCount()).isEqualTo(2);
        assertThat(snd.waitCount()).isEqualTo(2);
        assertThat(result.stream().noneMatch(x -> x.bookId() == empty)).isTrue();
        assertThat(repository.topQueuedBooks(snapshot().plusDays(3)).stream()
                .filter(x -> x.bookId() == empty).findFirst().orElseThrow().waitCount()).isEqualTo(1);
        assertThat(result.indexOf(fst) < result.indexOf(snd)).isEqualTo(first < second);

        jdbc.update("UPDATE book_reservations SET status='CANCELLED' WHERE id=?", secondPending);
        var afterCancel = repository.topQueuedBooks(snapshot());
        assertThat(afterCancel.stream().filter(x -> x.bookId() == second).findFirst().orElseThrow().waitCount())
                .isEqualTo(1);
        reserve(second, reader, "PENDING");
        assertThat(repository.topQueuedBooks(snapshot()).stream()
                .filter(x -> x.bookId() == second).findFirst().orElseThrow().waitCount()).isEqualTo(2);
    }

    @Test
    void limitsToTenAndOrdersTiesByBookId() {
        long reader = reader();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long id = book();
            ids.add(id);
            reserve(id, reader, "PENDING");
        }
        var idsInResult = repository.topQueuedBooks(snapshot()).stream()
                .filter(entry -> ids.contains(entry.bookId()))
                .map(entry -> entry.bookId()).toList();
        // Other pre-existing titles can also be in Top 10; compare the order of fixture titles.
        assertThat(idsInResult).isSorted();
        assertThat(repository.topQueuedBooks(snapshot())).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void realReportUnderThreeSecondsWhenDatabaseHasAtLeastFiveHundredLoanSlips() {
        Long loanCount = jdbc.queryForObject("SELECT COUNT(*) FROM loans", Long.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(loanCount != null && loanCount >= 500,
                "Cần nạp tối thiểu 500 phiếu mượn và dữ liệu đặt giữ trước khi benchmark.");
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                service.report(LocalDate.now().minusDays(29), LocalDate.now()));
    }
}
