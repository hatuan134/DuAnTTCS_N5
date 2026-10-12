package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.CreateFeeReductionRequest;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.OverdueFeeRepository;
import com.duanttcsn5.library.service.FeeReductionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in disposable PostgreSQL only. UUID fixture history is retained, like S4-01.1 tests. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_01_2_DB_TEST", matches = "true")
class FeeReductionDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired OverdueFeeRepository fees;
    @Autowired FeeReductionService service;
    record Fixture(Long staff, Long otherStaff, Long manager, Long reader, Long item, Long fee) {}

    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id",
                Long.class, "S4012 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture(long amount) {
        Long staff = user("LIBRARIAN"), other = user("LIBRARIAN"), manager = user("LIBRARY_MANAGER"), reader = user("READER");
        Long book = jdbc.queryForObject("INSERT INTO books(title, author_id, category_id) VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id",
                Long.class, "S4012 " + UUID.randomUUID());
        Long copy = jdbc.queryForObject("INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition) VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD') RETURNING id",
                Long.class, book, "S4012-" + UUID.randomUUID());
        OffsetDateTime returned = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime due = returned.minusDays(3), borrowed = returned.minusDays(8);
        Long loan = loans.insert(null, reader, staff, "S4012-" + UUID.randomUUID(), borrowed);
        loans.insertItem(loan, copy, borrowed, due);
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        jdbc.update("UPDATE loan_items SET returned_at = ?, returned_by = ? WHERE id = ?", returned, staff, item);
        var snapshot = new ReturnFeeAssessment("READY", "Phí đã ghi nhận.", "S4-01.1-OPEN-DAYS-HALF-UP-V1",
                due, returned, amount == 0 ? 0 : 3, 5000L, amount, amount, null, List.of(), List.of(), List.of());
        Long fee = fees.record(item, reader, snapshot).feeId();
        return new Fixture(staff, other, manager, reader, item, fee);
    }
    CreateFeeReductionRequest request(long amount) {
        return new CreateFeeReductionRequest(BigDecimal.valueOf(amount), "Bạn đọc có hoàn cảnh khó khăn");
    }
    long count(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM fee_reduction_requests WHERE reader_fee_id = ? AND status = 'PENDING'", Long.class, f.fee());
    }
    @Test void demoPartialRequestPreservesFeePaymentAndAssessmentAndManagerSeesIt() {
        var f = fixture(15000);
        String before = jdbc.queryForObject("SELECT snapshot::text FROM return_fee_assessments WHERE item_id = ?", String.class, f.item());
        var saved = service.create(f.fee(), request(10000), f.staff());
        assertThat(saved.status()).isEqualTo("PENDING");
        assertThat(saved.requestedAmountVnd()).isEqualTo(10000);
        assertThat(saved.originalAmountVnd()).isEqualTo(15000);
        assertThat(saved.outstandingAmountVnd()).isEqualTo(15000);
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM reader_fees WHERE id = ?", Long.class, f.fee())).isEqualTo(15000);
        assertThat(jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id = ?", Long.class, f.fee())).isZero();
        assertThat(jdbc.queryForObject("SELECT snapshot::text FROM return_fee_assessments WHERE item_id = ?", String.class, f.item())).isEqualTo(before);
        assertThat(service.detail(f.fee(), f.manager()).pendingRequest()).isEqualTo(saved);
        assertThat(service.pending(0, f.manager()).items()).extracting(r -> r.id()).contains(saved.id());
        assertThat(count(f)).isEqualTo(1);
    }
    @Test void fullRequestKeepsReceivableAndDuplicateIsRejected() {
        var f = fixture(15000);
        assertThat(service.create(f.fee(), request(15000), f.staff()).outstandingAmountVnd()).isEqualTo(15000);
        assertThatThrownBy(() -> service.create(f.fee(), request(10000), f.otherStaff()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("FEE_REDUCTION_PENDING"));
        assertThat(count(f)).isEqualTo(1);
    }
    @Test void zeroAssessmentCreatesNoReceivableAndUnrelatedFeesAreNotEligible() {
        var f = fixture(0);
        assertThat(f.fee()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reader_fees WHERE return_item_id = ?", Long.class, f.item())).isZero();
        Long unrelated = jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id, amount_vnd) VALUES (?, 15000) RETURNING id", Long.class, f.reader());
        assertThatThrownBy(() -> service.create(unrelated, request(10000), f.staff()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("OVERDUE_FEE_NOT_FOUND"));
    }
    @Test void anyPaymentPreventsRequest() {
        var f = fixture(15000);
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd = 1000 WHERE id = ?", f.fee());
        assertThatThrownBy(() -> service.create(f.fee(), request(10000), f.staff()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("FEE_REDUCTION_NOT_ALLOWED"));
        assertThat(count(f)).isZero();
    }
    @Test void databaseGuardRejectsForgedOriginalAmountAndNonLibrarianRequester() {
        var f = fixture(15000);
        String sql = "INSERT INTO fee_reduction_requests(reader_fee_id, requested_by, requester_name, original_amount_vnd, requested_amount_vnd, reason) VALUES (?, ?, 'Giả mạo', ?, 10000, 'Lý do')";
        assertThatThrownBy(() -> jdbc.update(sql, f.fee(), f.manager(), 15000L)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(sql, f.fee(), f.staff(), 99999L)).isInstanceOf(DataAccessException.class);
        assertThat(count(f)).isZero();
    }
    @Test void simultaneousSubmissionsCreateOnePendingRequestAndPreserveReceivable() throws Exception {
        var f = fixture(15000);
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            Callable<String> first = () -> attempt(f, f.staff(), barrier);
            Callable<String> second = () -> attempt(f, f.otherStaff(), barrier);
            var a = pool.submit(first); var b = pool.submit(second);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "FEE_REDUCTION_PENDING");
            assertThat(count(f)).isEqualTo(1);
            assertThat(service.detail(f.fee(), f.manager()).outstandingAmountVnd()).isEqualTo(15000);
        } finally { pool.shutdownNow(); }
    }
    String attempt(Fixture f, Long actor, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try { service.create(f.fee(), request(10000), actor); return "OK"; }
        catch (ApiException e) { return e.getCode(); }
    }
}
