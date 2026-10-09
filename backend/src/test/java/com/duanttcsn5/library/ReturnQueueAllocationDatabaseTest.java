package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ConfirmReturnRequest;
import com.duanttcsn5.library.dto.loan.ConfirmReturnResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Opt in only on disposable PostgreSQL with V1..V33 and a valid library calendar.
 * UUID fixtures are committed for real concurrency; immutable loan history is retained.
 */
@SpringBootTest(properties = {"app.bootstrap-admin.password=", "app.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named = "S3_07_3_DB_TEST", matches = "true")
class ReturnQueueAllocationDatabaseTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;
    @Autowired BookReservationService reservations;
    @Autowired LibraryConfigurationService calendar;

    private record Fixture(Long staff, Long reader, Long book, Long copy, Long loan, Long item, String barcode) {
        ConfirmReturnRequest request() { return new ConfirmReturnRequest(barcode, item); }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS); }

    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3073 " + role, UUID.randomUUID() + "@example.invalid", role);
    }

    private Fixture fixture() {
        Long staff = user("LIBRARIAN"), reader = user("READER");
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3073 " + UUID.randomUUID());
        return borrowedCopy(staff, reader, book);
    }

    private Fixture borrowedCopy(Long staff, Long reader, Long book) {
        String barcode = "S3073-" + UUID.randomUUID();
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 7, 50000, 'GOOD') RETURNING id
                """, Long.class, book, barcode);
        OffsetDateTime borrowed = now().minusDays(7);
        Long loan = loans.insert(null, reader, staff, "S3073-" + UUID.randomUUID(), borrowed);
        loans.insertItem(loan, copy, borrowed, borrowed.plusDays(14));
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        return new Fixture(staff, reader, book, copy, loan, item, barcode);
    }

    private Long readerWithCard(String cardStatus, LocalDate expiresAt) {
        Long reader = user("READER");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 5, 14, 1, 7) RETURNING id
                """, Long.class, "S3073-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "S3073-" + UUID.randomUUID(), reader, type,
                LocalDate.now(ZONE).minusYears(1), expiresAt, cardStatus);
        return reader;
    }

    private Long validReader() { return readerWithCard("ACTIVE", LocalDate.now(ZONE).plusYears(1)); }

    private Long pending(Fixture f, Long reader, OffsetDateTime reservedAt) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
                VALUES (?, ?, 'PENDING', ?) RETURNING id
                """, Long.class, f.book(), reader, reservedAt);
    }

    private String copyStatus(Fixture f) {
        return jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy());
    }

    private void pendingUnchanged(Long id, OffsetDateTime original) {
        var row = jdbc.queryForMap("SELECT status, book_copy_id, reserved_at, hold_started_at, pickup_deadline FROM book_reservations WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("book_copy_id")).isNull(); assertThat(row.get("hold_started_at")).isNull();
        assertThat(row.get("pickup_deadline")).isNull();
        assertThat(jdbc.queryForObject("SELECT reserved_at FROM book_reservations WHERE id = ?", OffsetDateTime.class, id)
                .toInstant()).isEqualTo(original.toInstant());
    }

    private void allocated(Fixture f, Long id, OffsetDateTime original, ConfirmReturnResponse response) {
        assertThat(copyStatus(f)).isEqualTo("HELD");
        assertThat(response.copyStatus()).isEqualTo("HELD"); assertThat(response.nextReservationId()).isEqualTo(id);
        var row = jdbc.queryForMap("SELECT status, book_copy_id FROM book_reservations WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("READY_FOR_PICKUP"); assertThat(row.get("book_copy_id")).isEqualTo(f.copy());
        assertThat(jdbc.queryForObject("SELECT reserved_at FROM book_reservations WHERE id = ?", OffsetDateTime.class, id)
                .toInstant()).isEqualTo(original.toInstant());
        assertThat(response.holdStartedAt().toInstant()).isEqualTo(response.returnedAt().toInstant());
        assertThat(response.pickupDeadline().toInstant())
                .isEqualTo(calendar.calculateReservationPickupDeadline(response.returnedAt()).toInstant());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_reservations WHERE book_copy_id = ? AND status = 'READY_FOR_PICKUP'",
                Long.class, f.copy())).isEqualTo(1L);
    }

    @Test void noQueueReturnsAvailableAndPreservesReturnHistory() {
        var f = fixture(); var result = service.confirmReturn(f.request(), f.staff());
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE"); assertThat(result.nextReservationId()).isNull();
        assertThat(result.itemStatus()).isEqualTo("RETURNED"); assertThat(result.returnedById()).isEqualTo(f.staff());
        assertThat(service.loanDetail(f.loan(), f.staff()).items().get(0).returnedAt().toInstant())
                .isEqualTo(result.returnedAt().toInstant());
    }

    @Test void oneWaiterGetsReturnedCopyImmediatelyAndOldPickupViewsSeeIt() {
        var f = fixture(); Long reader = validReader(); OffsetDateTime placed = now().minusDays(2);
        Long order = pending(f, reader, placed); var result = service.confirmReturn(f.request(), f.staff());
        allocated(f, order, placed, result);
        assertThat(reservations.getMyReservations(reader)).anySatisfy(r -> {
            assertThat(r.id()).isEqualTo(order); assertThat(r.status()).isEqualTo("READY_FOR_PICKUP");
        });
        assertThat(service.pickupContext(order).copyStatus()).isEqualTo("HELD");
    }

    @Test void fifoUsesOriginalTimeThenIdAndDoesNotChangeRemainingOrders() {
        var f = fixture(); OffsetDateTime firstTime = now().minusDays(2), laterTime = now().minusDays(1);
        // Insert the later timestamp first, so id order alone would choose the wrong person.
        Long later = pending(f, validReader(), laterTime);
        Long first = pending(f, validReader(), firstTime), tied = pending(f, validReader(), firstTime);
        var result = service.confirmReturn(f.request(), f.staff()); allocated(f, first, firstTime, result);
        pendingUnchanged(tied, firstTime); pendingUnchanged(later, laterTime);
        assertThat(jdbc.queryForList("SELECT id FROM book_reservations WHERE book_id = ? AND status = 'PENDING' ORDER BY reserved_at, id",
                Long.class, f.book())).containsExactly(tied, later);
    }

    @Test void expiredLockedMissingCardAndInactiveAccountAreSkippedWithoutCancellingThem() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2);
        List<Long> invalidReaders = List.of(
                readerWithCard("ACTIVE", LocalDate.now(ZONE).minusDays(1)),
                readerWithCard("EXPIRED", LocalDate.now(ZONE).plusDays(5)),
                readerWithCard("LOCKED", LocalDate.now(ZONE).plusDays(5)), user("READER"), validReader());
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", invalidReaders.get(4));
        var skipped = invalidReaders.stream().map(reader -> pending(f, reader, placed)).toList();
        Long valid = pending(f, validReader(), placed.plusHours(1));
        var result = service.confirmReturn(f.request(), f.staff()); allocated(f, valid, placed.plusHours(1), result);
        for (Long order : skipped) pendingUnchanged(order, placed);
    }

    @Test void cancelledExpiredAndFulfilledOrdersDoNotBlockPendingHead() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2);
        for (String status : List.of("CANCELLED", "EXPIRED", "FULFILLED")) {
            Long old = pending(f, validReader(), placed); jdbc.update("UPDATE book_reservations SET status = ? WHERE id = ?", status, old);
        }
        Long head = pending(f, validReader(), placed.plusDays(1));
        allocated(f, head, placed.plusDays(1), service.confirmReturn(f.request(), f.staff()));
    }

    @Test void entirelyIneligibleQueueReturnsAvailableAndPreservesAllOrders() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2);
        Long order = pending(f, readerWithCard("LOCKED", LocalDate.now(ZONE).plusYears(1)), placed);
        var result = service.confirmReturn(f.request(), f.staff());
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE"); assertThat(result.nextReservationId()).isNull();
        pendingUnchanged(order, placed);
    }

    @Test void cardExpiryDayIsStillEligible() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2);
        Long order = pending(f, readerWithCard("ACTIVE", LocalDate.now(ZONE)), placed);
        allocated(f, order, placed, service.confirmReturn(f.request(), f.staff()));
    }

    @Test void borrowedToHeldNeverVisitsAvailableEvenInsideTheTransaction() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2); Long order = pending(f, validReader(), placed);
        String constraint = "s3073_no_available_" + f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copies ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.copy() + " OR status <> 'AVAILABLE')");
            allocated(f, order, placed, service.confirmReturn(f.request(), f.staff()));
        } finally { jdbc.execute("ALTER TABLE book_copies DROP CONSTRAINT IF EXISTS " + constraint); }
    }

    @Test void failedReturnRollsBackBothQueueAllocationAndCopyStatus() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2); Long order = pending(f, validReader(), placed);
        String constraint = "s3073_fail_return_" + f.item();
        try {
            jdbc.execute("ALTER TABLE loan_items ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.item() + " OR returned_at IS NULL)");
            assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff())).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getCode()).isEqualTo("RETURN_SAVE_FAILED"));
            pendingUnchanged(order, placed); assertThat(copyStatus(f)).isEqualTo("BORROWED");
            var row = jdbc.queryForMap("SELECT returned_at, returned_by, returned_by_name FROM loan_items WHERE id = ?", f.item());
            assertThat(row.values()).containsOnlyNulls();
        } finally { jdbc.execute("ALTER TABLE loan_items DROP CONSTRAINT IF EXISTS " + constraint); }
        allocated(f, order, placed, service.confirmReturn(f.request(), f.staff()));
    }

    @Test void uniqueIndexPreventsOneHeldCopyFromBelongingToTwoReadyOrders() {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2);
        Long first = pending(f, validReader(), placed), second = pending(f, validReader(), placed.plusHours(1));
        var result = service.confirmReturn(f.request(), f.staff()); allocated(f, first, placed, result);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE book_reservations SET status = 'READY_FOR_PICKUP', book_copy_id = ?,
                    hold_started_at = ?, pickup_deadline = ? WHERE id = ?
                """, f.copy(), result.returnedAt(), result.pickupDeadline(), second))
                .isInstanceOf(DataIntegrityViolationException.class);
        pendingUnchanged(second, placed.plusHours(1));
    }

    @Test void simultaneousConfirmationsReturnExactlyOnceAndAllocateExactlyOnce() throws Exception {
        var f = fixture(); OffsetDateTime placed = now().minusDays(2); Long head = pending(f, validReader(), placed);
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.confirmReturn(f.request(), f.staff()); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            };
            var a = pool.submit(attempt); var b = pool.submit(attempt);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "LOAN_ALREADY_RETURNED");
            allocated(f, head, placed, loans.findReturnConfirmation(f.item()).orElseThrow());
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @Test void twoConcurrentReturnsOfSameTitleGetDistinctHeadsAndKeepThirdWaiting() throws Exception {
        var f = fixture(); var other = borrowedCopy(f.staff(), f.reader(), f.book());
        OffsetDateTime placed = now().minusDays(2);
        Long first = pending(f, validReader(), placed), second = pending(f, validReader(), placed.plusHours(1));
        Long third = pending(f, validReader(), placed.plusHours(2));
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var a = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return service.confirmReturn(f.request(), f.staff()); });
            var b = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return service.confirmReturn(other.request(), other.staff()); });
            var ra = a.get(30, TimeUnit.SECONDS); var rb = b.get(30, TimeUnit.SECONDS);
            assertThat(List.of(ra.nextReservationId(), rb.nextReservationId())).containsExactlyInAnyOrder(first, second);
            assertThat(copyStatus(f)).isEqualTo("HELD"); assertThat(copyStatus(other)).isEqualTo("HELD");
            pendingUnchanged(third, placed.plusHours(2));
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
}
