package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Opt in on a disposable PostgreSQL 15 test DB with S3_01_1_DB_TEST=true. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_01_1_DB_TEST", matches = "true")
class ReservationLoanDatabaseTest {
    @Autowired private LoanService service;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(Long reader, Long staff, Long book, Long copy, Long reservation, String card) {}

    private Long user(String role, String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3-01.1 " + role, role + suffix + "@example.invalid", role);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long reader = user("READER", suffix), staff = user("LIBRARIAN", suffix);
        String card = "S3011-" + suffix;
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, (SELECT MIN(id) FROM card_types), CURRENT_DATE - 1, CURRENT_DATE + 90, 'ACTIVE')
                """, card, reader);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3-01.1 " + suffix);
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3011-" + suffix);
        OffsetDateTime at = OffsetDateTime.now().minusDays(10);
        Long reservation = jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, 'READY_FOR_PICKUP', ?, ?) RETURNING id
                """, Long.class, book, reader, copy, at, at.plusDays(3));
        return new Fixture(reader, staff, book, copy, reservation, card);
    }

    @Test @Transactional
    void persistsExactReaderHeldCopyAndSourceWithNoDueDateAndPreventsSecondConversion() {
        Fixture f = fixture();
        var result = service.createFromReservation(f.reservation(), f.staff(), f.card());
        assertThat(jdbc.queryForObject("SELECT borrower_user_id FROM loans WHERE id = ?", Long.class, result.id()))
                .isEqualTo(f.reader());
        assertThat(jdbc.queryForObject("SELECT reservation_id FROM loans WHERE id = ?", Long.class, result.id()))
                .isEqualTo(f.reservation());
        assertThat(jdbc.queryForObject("SELECT book_copy_id FROM loan_items WHERE loan_id = ?", Long.class, result.id()))
                .isEqualTo(f.copy());
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE loan_id = ?", OffsetDateTime.class, result.id()))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy()))
                .isEqualTo("BORROWED");
        assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, f.reservation()))
                .isEqualTo("READY_FOR_PICKUP");
        assertThat(service.pickupContext(f.reservation()).loanNumber()).isEqualTo(result.loanNumber());
        assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), f.card()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RESERVATION_ALREADY_CONVERTED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE reservation_id = ?", Long.class, f.reservation()))
                .isEqualTo(1L);
    }

    @Test @Transactional
    void incorrectAndBlankCardsLeaveLoanAndHoldUntouched() {
        Fixture f = fixture();
        for (String card : new String[]{"WRONG", "", "   "}) {
            assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), card))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE reservation_id = ?", Long.class, f.reservation()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy())).isEqualTo("HELD");
    }

    @Test @Transactional
    void migrationRetainsLegacyAvailableCopyBorrowAndReturnBehavior() {
        Fixture f = fixture();
        jdbc.update("UPDATE book_copies SET status = 'AVAILABLE' WHERE id = ?", f.copy());
        Long loan = jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, created_by)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, "LEGACY-" + UUID.randomUUID(), f.reader(), f.staff());
        jdbc.update("INSERT INTO loan_items(loan_id, book_copy_id) VALUES (?, ?)", loan, f.copy());
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy())).isEqualTo("BORROWED");
        jdbc.update("UPDATE loan_items SET returned_at = clock_timestamp() WHERE loan_id = ?", loan);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy())).isEqualTo("AVAILABLE");
    }

    @Test
    void failureInLoanItemRollsBackHeaderAndLeavesOriginalHold() {
        Fixture f = fixture();
        String constraint = "s3011_fail_item_" + f.copy();
        try {
            jdbc.execute("ALTER TABLE loan_items ADD CONSTRAINT " + constraint
                    + " CHECK (book_copy_id <> " + f.copy() + ")");
            assertThatThrownBy(() -> service.createFromReservation(f.reservation(), f.staff(), f.card()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE reservation_id = ?", Long.class, f.reservation()))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy()))
                    .isEqualTo("HELD");
        } finally {
            jdbc.execute("ALTER TABLE loan_items DROP CONSTRAINT IF EXISTS " + constraint);
            jdbc.update("DELETE FROM book_reservations WHERE id = ?", f.reservation());
            jdbc.update("DELETE FROM book_copies WHERE id = ?", f.copy());
            jdbc.update("DELETE FROM books WHERE id = ?", f.book());
            jdbc.update("DELETE FROM library_cards WHERE user_id = ?", f.reader());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", f.reader(), f.staff());
        }
    }

    @Test
    void simultaneousConfirmationsCreateExactlyOneLoan() throws Exception {
        // Worker transactions need committed fixtures. This one test retains its UUID
        // data in the disposable test DB because the existing history triggers prohibit deletion.
        Fixture f = fixture();
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.createFromReservation(f.reservation(), f.staff(), f.card()); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            };
            var first = pool.submit(attempt); var second = pool.submit(attempt);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "RESERVATION_ALREADY_CONVERTED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE reservation_id = ?", Long.class, f.reservation()))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.id = li.loan_id WHERE l.reservation_id = ?
                    """, Long.class, f.reservation())).isEqualTo(1L);
        } finally {
            pool.shutdownNow(); pool.awaitTermination(20, TimeUnit.SECONDS);
        }
    }
}
