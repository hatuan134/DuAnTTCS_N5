package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ConfirmReturnRequest;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Run ONLY on disposable PostgreSQL. Committed UUID fixtures are deliberately retained,
 * as the existing immutable loan-item trigger forbids deleting loan history.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_07_2_DB_TEST", matches = "true")
class ReturnConfirmationDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanService service;
    @Autowired LoanRepository loans;
    private record Fixture(Long staff, Long reader, Long loan, Long item, Long copy, String barcode,
                           OffsetDateTime dueAt) {
        ConfirmReturnRequest request() { return new ConfirmReturnRequest(barcode, item); }
    }
    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3072 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    private Long copy(Long book, String barcode) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD') RETURNING id
                """, Long.class, book, barcode);
    }
    private Fixture fixture(boolean overdue) {
        Long staff = user("LIBRARIAN"), reader = user("READER");
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3072 " + UUID.randomUUID());
        String barcode = "S3072-" + UUID.randomUUID(); Long copy = copy(book, barcode);
        OffsetDateTime borrowed = OffsetDateTime.now().minusDays(7).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime due = overdue ? borrowed.plusDays(2) : borrowed.plusDays(14);
        Long loan = loans.insert(null, reader, staff, "S3072-" + UUID.randomUUID(), borrowed);
        loans.insertItem(loan, copy, borrowed, due);
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ? AND book_copy_id = ?",
                Long.class, loan, copy);
        return new Fixture(staff, reader, loan, item, copy, barcode, due);
    }
    private String copyStatus(Fixture f) {
        return jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy());
    }
    private void unchanged(Fixture f) {
        assertThat(copyStatus(f)).isEqualTo("BORROWED");
        var row = jdbc.queryForMap("SELECT returned_at, returned_by, returned_by_name FROM loan_items WHERE id = ?", f.item());
        assertThat(row.get("returned_at")).isNull(); assertThat(row.get("returned_by")).isNull();
        assertThat(row.get("returned_by_name")).isNull();
        assertThat(loans.findReturnLookup(f.barcode()).orElseThrow().itemId()).isEqualTo(f.item());
    }
    private void returns(boolean overdue) {
        var f = fixture(overdue);
        assertThat(service.lookupReturn(f.barcode(), f.staff()).status()).isEqualTo(overdue ? "OVERDUE" : "ON_TIME");
        Instant before = Instant.now(); var result = service.confirmReturn(f.request(), f.staff());
        assertThat(result.returnedAt().toInstant()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(result.returnedAt().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        assertThat(result.loanStatus()).isEqualTo("RETURNED"); assertThat(result.itemStatus()).isEqualTo("RETURNED");
        assertThat(result.returnedById()).isEqualTo(f.staff()); assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
        assertThat(service.loanDetail(f.loan(), f.staff()).items().get(0).returnedAt().toInstant())
                .isEqualTo(result.returnedAt().toInstant());
        assertThat(service.loanDetail(f.loan(), f.staff()).items().get(0).dueAt().toInstant()).isEqualTo(f.dueAt().toInstant());
        assertThat(service.lookupReturn(f.barcode(), f.staff()).status()).isEqualTo("NOT_BORROWED");
        assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOAN_ALREADY_RETURNED"));
        assertThat(loans.findReturnConfirmation(f.item()).orElseThrow().returnedAt().toInstant())
                .isEqualTo(result.returnedAt().toInstant());
    }
    @Test void returnsOnTimeCopyWithPersistedReceiverAndDate() { returns(false); }
    @Test void overdueCopyIsReturnedWithoutChangingDeadlineOrCreatingFees() { returns(true); }
    @Test void failureAfterCopyTriggerRunsRollsBackItemDateReceiverAndCopyTogether() {
        var f = fixture(false); String constraint = "s3072_fail_item_" + f.item();
        try {
            // The BEFORE return trigger changes the copy before PostgreSQL evaluates this CHECK.
            jdbc.execute("ALTER TABLE loan_items ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.item() + " OR returned_at IS NULL)");
            assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff()))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("RETURN_SAVE_FAILED"));
            unchanged(f);
        } finally { jdbc.execute("ALTER TABLE loan_items DROP CONSTRAINT IF EXISTS " + constraint); }
        assertThat(service.confirmReturn(f.request(), f.staff()).copyStatus()).isEqualTo("AVAILABLE");
    }
    @Test void failedCopyUpdateRollsBackTheEntireReturn() {
        var f = fixture(false); String constraint = "s3072_fail_copy_" + f.copy();
        try {
            jdbc.execute("ALTER TABLE book_copies ADD CONSTRAINT " + constraint
                    + " CHECK (id <> " + f.copy() + " OR status <> 'AVAILABLE')");
            assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff())).isInstanceOf(ApiException.class);
            unchanged(f);
        } finally { jdbc.execute("ALTER TABLE book_copies DROP CONSTRAINT IF EXISTS " + constraint); }
    }
    @Test void oneOfMultipleItemsIsReturnedAndSiblingRemainsBorrowed() {
        var f = fixture(false); Long book = jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id = ?", Long.class, f.copy());
        String barcode = "S3072-" + UUID.randomUUID(); Long sibling = copy(book, barcode);
        loans.insertItem(f.loan(), sibling, f.dueAt().minusDays(14), f.dueAt());
        assertThat(service.confirmReturn(f.request(), f.staff()).loanStatus()).isEqualTo("BORROWED");
        var lookup = loans.findReturnLookup(barcode).orElseThrow();
        assertThat(lookup.itemId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, sibling)).isEqualTo("BORROWED");
        assertThat(service.confirmReturn(new ConfirmReturnRequest(barcode, lookup.itemId()), f.staff()).loanStatus()).isEqualTo("RETURNED");
    }
    @Test void staleConfirmationCannotReturnNewLoanOfTheSameBarcode() {
        var f = fixture(false); service.confirmReturn(f.request(), f.staff());
        OffsetDateTime borrowed = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        Long later = loans.insert(null, f.reader(), f.staff(), "S3072-" + UUID.randomUUID(), borrowed);
        loans.insertItem(later, f.copy(), borrowed, borrowed.plusDays(14));
        assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff())).isInstanceOf(ApiException.class);
        assertThat(loans.findReturnLookup(f.barcode()).orElseThrow().loanId()).isEqualTo(later);
        assertThat(copyStatus(f)).isEqualTo("BORROWED");
    }
    @Test void pendingReservationIsNotAllocatedByThisSlice() {
        var f = fixture(false); Long book = jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id = ?", Long.class, f.copy());
        Long reservation = jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status)
                VALUES (?, ?, 'PENDING') RETURNING id
                """, Long.class, book, user("READER"));
        service.confirmReturn(f.request(), f.staff());
        assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
        var row = jdbc.queryForMap("SELECT status, book_copy_id FROM book_reservations WHERE id = ?", reservation);
        assertThat(row.get("status")).isEqualTo("PENDING"); assertThat(row.get("book_copy_id")).isNull();
    }
    @Test void receiverSnapshotSurvivesAccountRenameAndDeletion() {
        var f = fixture(false); Long receiver = user("LIBRARIAN");
        var result = service.confirmReturn(f.request(), receiver);
        jdbc.update("UPDATE users SET full_name = 'Tên mới' WHERE id = ?", receiver);
        assertThat(loans.findReturnConfirmation(f.item()).orElseThrow().returnedByName()).isEqualTo(result.returnedByName());
        jdbc.update("DELETE FROM users WHERE id = ?", receiver);
        assertThat(loans.findReturnConfirmation(f.item()).orElseThrow().returnedById()).isEqualTo(receiver);
    }
    @Test void simultaneousConfirmationsAcceptExactlyOneAndPreserveItsStamp() throws Exception {
        var f = fixture(false); var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.confirmReturn(f.request(), f.staff()); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            };
            var a = pool.submit(attempt); var b = pool.submit(attempt);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "LOAN_ALREADY_RETURNED");
            assertThat(copyStatus(f)).isEqualTo("AVAILABLE");
            assertThat(loans.findReturnConfirmation(f.item()).orElseThrow().returnedById()).isEqualTo(f.staff());
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
}
