package com.duanttcsn5.library;

import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Run on a separate seeded PostgreSQL 15 DB. Calendar changes and fixtures roll back per test. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_01_2_DB_TEST", matches = "true")
@Transactional
class ReservationLoanDatesDatabaseTest {
    @Autowired private LoanService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private record Fixture(Long reservationId, Long actorId, String cardNumber) {}

    private void calendar() {
        jdbc.update("UPDATE library_weekly_schedule SET is_open = TRUE, open_time = '08:00', close_time = '17:00'");
        jdbc.update("DELETE FROM library_closed_dates");
    }

    private Fixture fixture(int days) {
        String suffix = UUID.randomUUID().toString();
        Long reader = user("READER", suffix), staff = user("LIBRARIAN", suffix);
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 3, ?, 1, 7) RETURNING id
                """, Long.class, "S3012-" + suffix, days);
        String card = "S3012-" + suffix;
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 90, 'ACTIVE')
                """, card, reader, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3012-" + suffix);
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3012-" + suffix);
        var reserved = OffsetDateTime.now().minusDays(10);
        Long reservation = jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, 'READY_FOR_PICKUP', ?, ?) RETURNING id
                """, Long.class, book, reader, copy, reserved, OffsetDateTime.now().plusDays(3));
        return new Fixture(reservation, staff, card);
    }

    private Long user(String role, String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3012 " + role, role + suffix + "@example.invalid", role);
    }

    @Test void previewUsesCardDaysAndConfirmationPersistsBothBorrowTimestampsAndDueTimestamp() {
        calendar();
        Fixture f = fixture(7);
        var dates = service.pickupContext(f.reservationId()).dates();
        assertThat(dates.loanDays()).isEqualTo(7);
        assertThat(dates.dueDate()).isEqualTo(dates.borrowDate().plusDays(7));
        var result = service.createFromReservation(f.reservationId(), f.actorId(), f.cardNumber(),
                dates.borrowDate(), dates.dueAt(), dates.loanDays());
        assertThat(jdbc.queryForObject("SELECT borrowed_at FROM loans WHERE id = ?", OffsetDateTime.class, result.id()).toInstant())
                .isEqualTo(result.borrowedAt().toInstant());
        assertThat(jdbc.queryForObject("SELECT borrowed_at FROM loan_items WHERE loan_id = ?", OffsetDateTime.class, result.id()).toInstant())
                .isEqualTo(result.borrowedAt().toInstant());
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE loan_id = ?", OffsetDateTime.class, result.id()).toInstant())
                .isEqualTo(result.dates().dueAt().toInstant());
    }

    @Test void multipleClosuresAreReflectedInPreviewAndPersistedExactly() {
        calendar();
        Fixture f = fixture(14);
        LocalDate original = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(14);
        for (int i = 0; i < 3; i++) jdbc.update(
                "INSERT INTO library_closed_dates(closed_date, reason) VALUES (?, ?)", original.plusDays(i), "S3-01.2 kiểm thử");
        var dates = service.pickupContext(f.reservationId()).dates();
        assertThat(dates.dueDate()).isEqualTo(original.plusDays(3));
        assertThat(dates.skippedClosedDates()).hasSize(3);
        var result = service.createFromReservation(f.reservationId(), f.actorId(), f.cardNumber(),
                dates.borrowDate(), dates.dueAt(), dates.loanDays());
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE loan_id = ?", OffsetDateTime.class, result.id()).toInstant())
                .isEqualTo(dates.dueAt().toInstant());
    }

    @Test void configurationErrorCanReturnAndCommitItsReadContextWithoutUnexpectedRollback() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var context = transaction.execute(status -> {
            Fixture f = fixture(14);
            var sunday = jdbc.queryForMap("SELECT is_open, open_time, close_time FROM library_weekly_schedule WHERE day_of_week = 7");
            jdbc.update("DELETE FROM library_weekly_schedule WHERE day_of_week = 7");
            try {
                // The owning transaction actually commits after the callback.
                // A nested read validation must not silently mark it rollback-only.
                return service.pickupContext(f.reservationId());
            } finally {
                jdbc.update("INSERT INTO library_weekly_schedule(day_of_week, is_open, open_time, close_time) VALUES (7, ?, ?, ?)",
                        sunday.get("is_open"), sunday.get("open_time"), sunday.get("close_time"));
                Long book = jdbc.queryForObject("SELECT book_id FROM book_reservations WHERE id = ?", Long.class, f.reservationId());
                Long copy = jdbc.queryForObject("SELECT book_copy_id FROM book_reservations WHERE id = ?", Long.class, f.reservationId());
                Long reader = jdbc.queryForObject("SELECT reader_id FROM book_reservations WHERE id = ?", Long.class, f.reservationId());
                Long type = jdbc.queryForObject("SELECT card_type_id FROM library_cards WHERE user_id = ?", Long.class, reader);
                jdbc.update("DELETE FROM book_reservations WHERE id = ?", f.reservationId());
                jdbc.update("DELETE FROM book_copies WHERE id = ?", copy);
                jdbc.update("DELETE FROM books WHERE id = ?", book);
                jdbc.update("DELETE FROM library_cards WHERE user_id = ?", reader);
                jdbc.update("DELETE FROM card_types WHERE id = ?", type);
                jdbc.update("DELETE FROM users WHERE id IN (?, ?)", reader, f.actorId());
            }
        });
        assertThat(context).isNotNull();
        assertThat(context.dates()).isNull();
        assertThat(context.dateError()).contains("7 ngày");
    }
}
