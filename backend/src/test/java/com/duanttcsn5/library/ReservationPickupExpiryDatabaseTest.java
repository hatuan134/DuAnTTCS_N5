package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.LoanService;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Run ONLY on a disposable PostgreSQL DB. Calls the real proxy outside a test transaction. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_01_4_DB_TEST", matches = "true")
class ReservationPickupExpiryDatabaseTest {
    @Autowired private LoanService service;
    @Autowired private BookReservationService reservations;
    @Autowired private JdbcTemplate jdbc;
    private record Fixture(Long reader, Long staff, Long book, Long copy, Long reservation, String card, Long cardType) {}

    private Long user(String role, String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3-01.4 " + role, role + suffix + "@example.invalid", role);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long reader = user("READER", suffix), staff = user("LIBRARIAN", suffix);
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 3, 14, 1, 7) RETURNING id
                """, Long.class, "S3014-" + suffix);
        String card = "S3014-" + suffix;
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 90, 'ACTIVE')
                """, card, reader, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3-01.4 " + suffix);
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3014-" + suffix);
        OffsetDateTime at = OffsetDateTime.now().minusDays(10);
        Long reservation = jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, 'READY_FOR_PICKUP', ?, ?) RETURNING id
                """, Long.class, book, reader, copy, at, at.plusDays(3));
        return new Fixture(reader, staff, book, copy, reservation, card, type);
    }


    private void assertState(Fixture f, String reservationStatus, String copyStatus) {
        assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, f.reservation()))
                .isEqualTo(reservationStatus);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy()))
                .isEqualTo(copyStatus);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE reservation_id = ?", Long.class, f.reservation())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loan_items WHERE book_copy_id = ?", Long.class, f.copy())).isZero();
    }
    private void confirmExpired(Fixture f) {
        assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), f.card()))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RESERVATION_PICKUP_EXPIRED");
                    assertThat(error.getMessage()).contains("đặt giữ lại");
                });
    }
    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM book_reservations WHERE book_id = ?", f.book());
        jdbc.update("DELETE FROM book_copies WHERE id = ?", f.copy());
        jdbc.update("DELETE FROM books WHERE id = ?", f.book());
        jdbc.update("DELETE FROM library_cards WHERE user_id = ?", f.reader());
        jdbc.update("DELETE FROM card_types WHERE id = ?", f.cardType());
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", f.reader(), f.staff());
    }
    @Test void conflictCommitsExpiryAndReleaseWithoutAllocatingTheNextReader() {
        Fixture f = fixture();
        Long waiter = user("READER", UUID.randomUUID().toString());
        try {
            Long next = jdbc.queryForObject("""
                    INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
                    VALUES (?, ?, 'PENDING', clock_timestamp()) RETURNING id
                    """, Long.class, f.book(), waiter);
            confirmExpired(f);
            assertState(f, "EXPIRED", "AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, next))
                    .isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT book_copy_id FROM book_reservations WHERE id = ?", Long.class, next)).isNull();
            assertThat(reservations.getReadyForPickup()).noneMatch(row -> f.reservation().equals(row.id()));
            assertThat(reservations.getQueueByBookId(f.book(), "EXPIRED").items())
                    .anyMatch(row -> f.reservation().equals(row.id()) && "EXPIRED".equals(row.status()));
            confirmExpired(f);
            assertState(f, "EXPIRED", "AVAILABLE");
        } finally { cleanup(f); jdbc.update("DELETE FROM users WHERE id = ?", waiter); }
    }
    @Test void openingExpiredOrderCommitsAndReloadsTheAvailableCopySummary() {
        Fixture f = fixture();
        try {
            var result = service.checkPickup(f.reservation(), f.staff());
            assertThat(result.expired()).isTrue();
            assertThat(result.copyStatus()).isEqualTo("AVAILABLE");
            assertThat(result.reservation().status()).isEqualTo("EXPIRED");
            assertState(f, "EXPIRED", "AVAILABLE");
            assertThat(service.pickupContext(f.reservation()).expired()).isTrue();
            assertThat(service.checkPickup(f.reservation(), f.staff()).copyStatus()).isEqualTo("AVAILABLE");
            assertState(f, "EXPIRED", "AVAILABLE");
        } finally { cleanup(f); }
    }
    @Test void failedCopyReleaseRollsBackTheAlreadyFlushedExpiry() {
        Fixture f = fixture();
        String constraint = "s3014_fail_release_" + f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copies ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.copy() + " OR status <> 'AVAILABLE')");
            assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), f.card()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertState(f, "READY_FOR_PICKUP", "HELD");
            assertThatThrownBy(() -> service.checkPickup(f.reservation(), f.staff()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertState(f, "READY_FOR_PICKUP", "HELD");
        } finally {
            jdbc.execute("ALTER TABLE book_copies DROP CONSTRAINT IF EXISTS " + constraint);
            cleanup(f);
        }
    }
    @Test void failedExpiryUpdateLeavesTheCopyHeldAndNoLoan() {
        Fixture f = fixture();
        String constraint = "s3014_fail_expiry_" + f.reservation();
        try {
            jdbc.execute("ALTER TABLE book_reservations ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.reservation() + " OR status <> 'EXPIRED')");
            assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), f.card()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertState(f, "READY_FOR_PICKUP", "HELD");
        } finally {
            jdbc.execute("ALTER TABLE book_reservations DROP CONSTRAINT IF EXISTS " + constraint);
            cleanup(f);
        }
    }
    @Test void concurrentExpiredConfirmationsCommitOnlyTheExpiryAndRelease() throws Exception {
        Fixture f = fixture();
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.createFromReservation(f.reservation(), f.staff(), f.card()); return "UNEXPECTED_LOAN"; }
                catch (ApiException error) { return error.getCode(); }
            };
            var first = pool.submit(attempt); var second = pool.submit(attempt);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactly("RESERVATION_PICKUP_EXPIRED", "RESERVATION_PICKUP_EXPIRED");
            assertState(f, "EXPIRED", "AVAILABLE");
        } finally {
            pool.shutdownNow(); pool.awaitTermination(20, TimeUnit.SECONDS);
            cleanup(f);
        }
    }
}
