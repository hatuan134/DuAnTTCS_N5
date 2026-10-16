package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
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

/** Opt in on a dedicated PostgreSQL 15 test DB with S2_09_4_DB_TEST=true. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_09_4_DB_TEST", matches = "true")
class StaffReservationCancellationDatabaseTest {
    @Autowired private BookReservationService service;
    @Autowired private LibraryConfigurationService calendar;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(Long bookId, Long readerId, Long staffId) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long reader = user("READER", "Bạn đọc " + suffix, "reader-" + suffix);
        Long staff = user("LIBRARIAN", "Thủ thư " + suffix, "staff-" + suffix);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S2094 " + suffix);
        return new Fixture(book, reader, staff);
    }

    private Long user(String role, String name, String emailPrefix) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, name, emailPrefix + "@example.invalid", role);
    }

    private Long heldCopy(Fixture f) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'HELD', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, f.bookId(), "S2094-" + UUID.randomUUID());
    }

    private Long add(Fixture f, String status, OffsetDateTime at, Long copyId) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at, book_copy_id, pickup_deadline)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, f.bookId(), f.readerId(), status, at, copyId,
                copyId == null ? null : at.plusDays(3));
    }

    private String reservationStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, id);
    }

    @Test @Transactional
    void pendingCancellationPersistsActorTimestampReasonAndRepositionsQueue() {
        Fixture f = fixture(); var at = OffsetDateTime.now().minusDays(2);
        Long first = add(f, "PENDING", at, null), second = add(f, "PENDING", at.plusHours(1), null);
        var result = service.cancelByStaff(first, f.staffId(), "  Bạn đọc đề nghị huỷ  ");
        assertThat(reservationStatus(first)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancelled_by FROM book_reservations WHERE id = ?", Long.class, first))
                .isEqualTo(f.staffId());
        assertThat(jdbc.queryForObject("SELECT cancelled_by_name FROM book_reservations WHERE id = ?", String.class, first))
                .isEqualTo(result.cancellation().actorName());
        assertThat(jdbc.queryForObject("SELECT cancelled_at FROM book_reservations WHERE id = ?", OffsetDateTime.class, first)
                .toInstant()).isEqualTo(result.cancellation().cancelledAt().toInstant());
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM book_reservations WHERE id = ?", String.class, first))
                .isEqualTo("Bạn đọc đề nghị huỷ");
        assertThat(service.getQueueByBookId(f.bookId(), "PENDING").items().get(0).id()).isEqualTo(second);
        assertThat(service.getQueueByBookId(f.bookId(), "PENDING").items().get(0).queuePosition()).isEqualTo(1L);
    }

    @Test @Transactional
    void readyCancellationTransfersSameCopyInFifoOrderWithoutUniqueAllocationViolation() {
        Fixture f = fixture(), other = fixture(); var at = OffsetDateTime.now().minusDays(2);
        Long copy = heldCopy(f);
        Long target = add(f, "READY_FOR_PICKUP", at, copy);
        Long late = add(f, "PENDING", at.plusHours(3), null);
        Long early = add(f, "PENDING", at.plusHours(2), null);
        Long tie = add(f, "PENDING", at.plusHours(2), null);
        add(other, "PENDING", at.minusHours(1), null);
        var result = service.cancelByStaff(target, f.staffId(), "Bạn đọc không đến nhận");
        assertThat(result.nextReservationId()).isEqualTo(early);
        assertThat(reservationStatus(target)).isEqualTo("CANCELLED");
        assertThat(reservationStatus(early)).isEqualTo("READY_FOR_PICKUP");
        assertThat(reservationStatus(late)).isEqualTo("PENDING"); assertThat(reservationStatus(tie)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT book_copy_id FROM book_reservations WHERE id = ?", Long.class, early)).isEqualTo(copy);
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_reservations WHERE book_copy_id = ? AND status = 'READY_FOR_PICKUP'",
                Long.class, copy)).isEqualTo(1L);
        assertThat(result.pickupDeadline().toInstant()).isEqualTo(
                calendar.calculateReservationPickupDeadline(result.cancellation().cancelledAt()).toInstant());
        var queue = service.getQueueByBookId(f.bookId(), "PENDING").items();
        assertThat(queue).extracting(r -> r.id()).containsExactly(tie, late);
        assertThat(queue).extracting(r -> r.queuePosition()).containsExactly(1L, 2L);
    }

    @Test @Transactional
    void readyWithoutWaitersReleasesCopyAndAuditSurvivesStaffAccountDeletion() {
        Fixture f = fixture(); var at = OffsetDateTime.now().minusDays(2); Long copy = heldCopy(f);
        Long target = add(f, "READY_FOR_PICKUP", at, copy);
        var result = service.cancelByStaff(target, f.staffId(), "Hết nhu cầu nhận sách");
        assertThat(result.copyOutcome()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy)).isEqualTo("AVAILABLE");
        jdbc.update("DELETE FROM users WHERE id = ?", f.staffId());
        assertThat(jdbc.queryForObject("SELECT cancelled_by FROM book_reservations WHERE id = ?", Long.class, target)).isNull();
        assertThat(jdbc.queryForObject("SELECT cancelled_by_name FROM book_reservations WHERE id = ?", String.class, target))
                .isEqualTo(result.cancellation().actorName());
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM book_reservations WHERE id = ?", String.class, target))
                .isEqualTo("Hết nhu cầu nhận sách");
    }

    @Test
    void databaseFailureAfterCancelledRowFlushRollsBackStatusAuditAndTransfer() {
        Fixture f = fixture(); var at = OffsetDateTime.now().minusDays(2); Long copy = heldCopy(f);
        Long target = add(f, "READY_FOR_PICKUP", at, copy);
        Long next = add(f, "PENDING", at.plusHours(1), null);
        // A scoped test-only constraint forces failure after the old READY row
        // has flushed. The service's independent transaction must roll back.
        String constraint = "s2094_fail_promotion_" + next;
        try {
            jdbc.execute("ALTER TABLE book_reservations ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + next + " OR status <> 'READY_FOR_PICKUP')");
            assertThatThrownBy(() -> service.cancelByStaff(target, f.staffId(), "Kiểm tra rollback"))
                    .isInstanceOf(RuntimeException.class);
            assertThat(reservationStatus(target)).isEqualTo("READY_FOR_PICKUP");
            assertThat(reservationStatus(next)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT cancelled_at FROM book_reservations WHERE id = ?",
                    OffsetDateTime.class, target)).isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy)).isEqualTo("HELD");
        } finally {
            jdbc.execute("ALTER TABLE book_reservations DROP CONSTRAINT IF EXISTS " + constraint);
            cleanup(f);
        }
    }

    @Test
    void twoConcurrentConfirmationsCommitOnceAndLeaveOneOriginalAudit() throws Exception {
        Fixture f = fixture(); Long copy = heldCopy(f);
        Long target = add(f, "READY_FOR_PICKUP", OffsetDateTime.now().minusDays(1), copy);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.cancelByStaff(target, f.staffId(), "Xác nhận đồng thời"); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            };
            Future<String> a = workers.submit(attempt), b = workers.submit(attempt);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "RESERVATION_NOT_CANCELLABLE");
            assertThat(reservationStatus(target)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM book_reservations WHERE id = ?", String.class, target))
                    .isEqualTo("Xác nhận đồng thời");
        } finally {
            workers.shutdownNow(); workers.awaitTermination(20, TimeUnit.SECONDS);
            // Only these UUID fixtures are committed; clean them after worker transactions end.
            cleanup(f);
        }
    }
    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM book_reservations WHERE book_id = ?", f.bookId());
        jdbc.update("DELETE FROM book_copies WHERE book_id = ?", f.bookId());
        jdbc.update("DELETE FROM books WHERE id = ?", f.bookId());
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", f.readerId(), f.staffId());
    }
}
