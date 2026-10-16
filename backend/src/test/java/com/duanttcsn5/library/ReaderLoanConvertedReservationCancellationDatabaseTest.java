package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL integration checks for S2-08.4.
 * Opt in with S2_08_4_DB_TEST=true using the project's normal local DB environment.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_08_4_DB_TEST", matches = "true")
@Transactional
class ReaderLoanConvertedReservationCancellationDatabaseTest {
    @Autowired private BookReservationService service;
    @Autowired private BookReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(Long bookId, Long readerId, Long staffId) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long readerId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Bạn đọc " + suffix, "reader-" + suffix + "@example.invalid");
        Long staffId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'LIBRARIAN' RETURNING id
                """, Long.class, "Thủ thư " + suffix, "staff-" + suffix + "@example.invalid");
        Long bookId = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S2-08.4 " + suffix);
        return new Fixture(bookId, readerId, staffId);
    }

    private Long heldCopy(Fixture fixture) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD')
                RETURNING id
                """, Long.class, fixture.bookId(), "S2084-" + UUID.randomUUID());
    }

    private Long reservation(Fixture fixture, Long readerId, String status, OffsetDateTime reservedAt, Long copyId) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, fixture.bookId(), readerId, copyId, status, reservedAt,
                copyId == null ? null : reservedAt.plusDays(3));
    }

    @Test
    void linkedLoanRejectsCancellationAndLeavesCopyAndQueueUntouched() {
        Fixture owner = fixture();
        String suffix = UUID.randomUUID().toString();
        Long waitingReaderId = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Bạn đọc chờ " + suffix, "waiting-" + suffix + "@example.invalid");
        OffsetDateTime reservedAt = OffsetDateTime.now().minusDays(1);
        Long copyId = heldCopy(owner);
        Long targetId = reservation(owner, owner.readerId(), "READY_FOR_PICKUP", reservedAt, copyId);
        Long waitingId = reservation(owner, waitingReaderId, "PENDING", reservedAt.plusHours(1), null);

        // Simulate the checkout transition with the minimal loan schema already present in the project.
        // The loan trigger requires AVAILABLE and then atomically moves the copy to BORROWED.
        jdbc.update("UPDATE book_copies SET status = 'AVAILABLE' WHERE id = ?", copyId);
        Long loanId = jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, "S2084-PM-" + UUID.randomUUID(), owner.readerId(), owner.staffId(),
                reservedAt.plusHours(2));
        jdbc.update("""
                INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at) VALUES (?, ?, ?)
                """, loanId, copyId, reservedAt.plusHours(2));

        assertThat(reservations.hasLoanLinkedToReservation(targetId)).isTrue();
        assertThatThrownBy(() -> service.cancelMine(targetId, owner.readerId()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RESERVATION_ALREADY_BORROWED");
                    assertThat(e.getMessage()).isEqualTo(
                            "Không thể huỷ đơn vì sách đã được nhận và đơn đã chuyển thành phiếu mượn.");
                });

        assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, targetId))
                .isEqualTo("READY_FOR_PICKUP");
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copyId))
                .isEqualTo("BORROWED");
        assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, waitingId))
                .isEqualTo("PENDING");
        assertThat(reservations.findPendingQueuePosition(waitingId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT book_copy_id FROM book_reservations WHERE id = ?", Long.class, targetId))
                .isEqualTo(copyId);
    }
}
