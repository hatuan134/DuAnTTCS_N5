package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt in only on a disposable PostgreSQL database. Committed UUID fixtures are retained. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_02_5_DB_TEST", matches = "true")
class DirectLoanConfirmationDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanService service;
    private record Fixture(Long reader, Long staff, Long type, Long book, String card, List<Long> copies, List<String> barcodes) {}

    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3025 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    private Fixture fixture(int number, int maximum) {
        Long reader = user("READER"), staff = user("LIBRARIAN");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, ?, 14, 1, 7) RETURNING id
                """, Long.class, "S3025-" + UUID.randomUUID(), maximum);
        String card = "S3025-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 30, 'ACTIVE')
                """, card, reader, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3025 " + UUID.randomUUID());
        List<Long> copies = new ArrayList<>(); List<String> barcodes = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            String barcode = "S3025-" + UUID.randomUUID(); barcodes.add(barcode);
            copies.add(jdbc.queryForObject("""
                    INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                    VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                    """, Long.class, book, barcode));
        }
        return new Fixture(reader, staff, type, book, card, copies, barcodes);
    }
    private CreateDirectLoanRequest request(Fixture f, UUID key) {
        return new CreateDirectLoanRequest(key, f.card(), f.barcodes());
    }
    private long loanCount(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE borrower_user_id = ?", Long.class, f.reader());
    }
    private String status(Long copy) {
        return jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy);
    }
    private void noLoan(Fixture f) {
        assertThat(loanCount(f)).isZero();
        for (Long copy : f.copies()) assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM loan_items WHERE book_copy_id = ?", Long.class, copy)).isZero();
    }
    private void succeeds(Fixture f, DirectLoanResponse result) {
        assertThat(loanCount(f)).isEqualTo(1);
        assertThat(result.loan().createdById()).isEqualTo(f.staff());
        assertThat(result.loan().readerId()).isEqualTo(f.reader());
        assertThat(result.loan().reservationId()).isNull();
        assertThat(result.loan().items()).hasSize(f.copies().size());
        assertThat(result.loan().items()).extracting(LoanDetailResponse.Item::barcode).containsExactlyElementsOf(f.barcodes());
        for (Long copy : f.copies()) {
            assertThat(status(copy)).isEqualTo("BORROWED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loan_items WHERE book_copy_id = ?", Long.class, copy)).isEqualTo(1);
        }
        assertThat(result.loan().items()).allSatisfy(item -> {
            assertThat(item.borrowedAt().toInstant()).isEqualTo(result.loan().borrowedAt().toInstant());
            assertThat(item.dueAt()).isAfter(item.borrowedAt());
        });
        assertThat(service.loanDetail(result.loan().id(), f.staff())).isEqualTo(result.loan());
    }
    @Test void confirmsOneCopyWithOneHeaderItemCreatorDatesAndBorrowedStatus() {
        var f = fixture(1, 5); succeeds(f, service.createDirectLoan(request(f, UUID.randomUUID()), f.staff()));
    }
    @Test void confirmsMultipleCopiesWithExactlyOneRowEachAndCurrentQuota() {
        var f = fixture(3, 5); var result = service.createDirectLoan(request(f, UUID.randomUUID()), f.staff());
        succeeds(f, result); assertThat(result.reader().borrowedBooks()).isEqualTo(3);
        assertThat(result.reader().remainingBooks()).isEqualTo(2);
    }
    @Test void errorOnSecondInsertRollsBackHeaderFirstItemAndBothCopyStatusChanges() {
        var f = fixture(2, 5); UUID key = UUID.randomUUID();
        String constraint = "s3025_fail_" + f.copies().get(1);
        try {
            // PostgreSQL executes the BORROWED trigger before CHECK; the first row has already succeeded.
            jdbc.execute("ALTER TABLE loan_items ADD CONSTRAINT " + constraint
                    + " CHECK (book_copy_id <> " + f.copies().get(1) + ")");
            assertThatThrownBy(() -> service.createDirectLoan(request(f, key), f.staff()))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.getCode()).isEqualTo("DIRECT_LOAN_SAVE_FAILED");
                        assertThat(error.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                    });
            noLoan(f); for (Long copy : f.copies()) assertThat(status(copy)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE direct_request_id = ?", Long.class, key)).isZero();
        } finally { jdbc.execute("ALTER TABLE loan_items DROP CONSTRAINT IF EXISTS " + constraint); }
        // The failed attempt did not consume its key and can be retried safely.
        succeeds(f, service.createDirectLoan(request(f, key), f.staff()));
    }
    @Test void reducedLimitAfterPreviewRejectsEntireBatch() {
        var f = fixture(2, 5);
        service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(0), List.of()), f.staff());
        service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(1), List.of(f.barcodes().get(0))), f.staff());
        jdbc.update("UPDATE card_types SET max_books = 1 WHERE id = ?", f.type());
        assertThatThrownBy(() -> service.createDirectLoan(request(f, UUID.randomUUID()), f.staff()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_LIMIT_EXCEEDED"));
        noLoan(f); for (Long copy : f.copies()) assertThat(status(copy)).isEqualTo("AVAILABLE");
    }
    @Test void anotherLoanAfterPreviewConsumesQuotaAndBlocksBatch() {
        var f = fixture(3, 2);
        service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(0), List.of()), f.staff());
        service.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.card(), List.of(f.barcodes().get(2))), f.staff());
        assertThatThrownBy(() -> service.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.card(),
                f.barcodes().subList(0, 2)), f.staff())).isInstanceOf(ApiException.class);
        assertThat(loanCount(f)).isEqualTo(1);
        assertThat(status(f.copies().get(0))).isEqualTo("AVAILABLE");
        assertThat(status(f.copies().get(1))).isEqualTo("AVAILABLE");
    }
    @Test void changedCopyStatusBeforeConfirmationKeepsOtherCopiesUntouched() {
        var f = fixture(2, 5);
        service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(1), List.of()), f.staff());
        jdbc.update("UPDATE book_copies SET status = 'REPAIR' WHERE id = ?", f.copies().get(1));
        assertThatThrownBy(() -> service.createDirectLoan(request(f, UUID.randomUUID()), f.staff())).isInstanceOf(ApiException.class);
        noLoan(f); assertThat(status(f.copies().get(0))).isEqualTo("AVAILABLE");
        assertThat(status(f.copies().get(1))).isEqualTo("REPAIR");
    }
    @Test void newlyCreatedHoldForEitherReaderIsRecheckedWithoutChangingTheOrder() {
        for (boolean own : List.of(false, true)) {
            var f = fixture(2, 5); Long owner = own ? f.reader() : user("READER");
            service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(1), List.of()), f.staff());
            // A stale AVAILABLE status cannot bypass the actual allocated reservation.
            Long order = jdbc.queryForObject("""
                    INSERT INTO book_reservations(book_id, reader_id, book_copy_id, status, reserved_at, pickup_deadline)
                    VALUES (?, ?, ?, 'READY_FOR_PICKUP', clock_timestamp() - INTERVAL '1 day',
                            clock_timestamp() + INTERVAL '1 day') RETURNING id
                    """, Long.class, f.book(), owner, f.copies().get(1));
            assertThatThrownBy(() -> service.createDirectLoan(request(f, UUID.randomUUID()), f.staff()))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(own
                            ? "LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER" : "LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER"));
            noLoan(f); for (Long copy : f.copies()) assertThat(status(copy)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id = ?", String.class, order))
                    .isEqualTo("READY_FOR_PICKUP");
        }
    }
    @Test void cardLockedExpiredOrAccountDisabledAfterPreviewBlocksConfirmation() {
        for (String change : List.of("LOCKED", "EXPIRED", "ACCOUNT")) {
            var f = fixture(1, 5);
            service.previewDirectLoanItem(new AddDirectLoanItemRequest(f.card(), f.barcodes().get(0), List.of()), f.staff());
            if ("ACCOUNT".equals(change)) jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", f.reader());
            else jdbc.update("UPDATE library_cards SET status = ? WHERE user_id = ?", change, f.reader());
            assertThatThrownBy(() -> service.createDirectLoan(request(f, UUID.randomUUID()), f.staff())).isInstanceOf(ApiException.class);
            noLoan(f); assertThat(status(f.copies().get(0))).isEqualTo("AVAILABLE");
        }
    }
    @Test void lostResponseRetryReturnsSameLoanAndDifferentPayloadCannotReuseKey() {
        var f = fixture(2, 5); UUID key = UUID.randomUUID();
        var first = service.createDirectLoan(request(f, key), f.staff());
        var retry = service.createDirectLoan(request(f, key), f.staff());
        assertThat(retry.loan()).isEqualTo(first.loan()); succeeds(f, retry);
        assertThatThrownBy(() -> service.createDirectLoan(new CreateDirectLoanRequest(key, f.card(),
                List.of(f.barcodes().get(0))), f.staff())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("DIRECT_LOAN_REQUEST_REUSED"));
        assertThat(loanCount(f)).isEqualTo(1);
    }
    @Test void simultaneousSameKeyReturnsOneLoanToBothRequests() throws Exception {
        var f = fixture(2, 5); var request = request(f, UUID.randomUUID());
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            Callable<DirectLoanResponse> attempt = () -> { barrier.await(10, TimeUnit.SECONDS); return service.createDirectLoan(request, f.staff()); };
            var first = pool.submit(attempt); var second = pool.submit(attempt);
            var result = first.get(30, TimeUnit.SECONDS);
            assertThat(second.get(30, TimeUnit.SECONDS).loan().id()).isEqualTo(result.loan().id()); succeeds(f, result);
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @Test void simultaneousDifferentDraftsForSameReaderCannotExceedQuota() throws Exception {
        var f = fixture(2, 1); var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var tasks = new ArrayList<Future<String>>();
            for (String barcode : f.barcodes()) tasks.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.card(), List.of(barcode)), f.staff()); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            }));
            assertThat(List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "LOAN_LIMIT_REACHED");
            assertThat(loanCount(f)).isEqualTo(1);
            assertThat(f.copies().stream().map(this::status).toList()).containsExactlyInAnyOrder("BORROWED", "AVAILABLE");
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
}
