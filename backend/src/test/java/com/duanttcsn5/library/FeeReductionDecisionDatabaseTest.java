package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in disposable PostgreSQL only: fee history is intentionally retained. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_01_3_DB_TEST", matches = "true")
class FeeReductionDecisionDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired OverdueFeeRepository fees;
    @Autowired FeeReductionService service;
    @Autowired PlatformTransactionManager transactions;
    record Fixture(Long staff, Long manager, Long otherManager, Long reader, Long item, Long fee, Long request) {}
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id",
                Long.class, "S4013 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture(long requested) {
        Long staff = user("LIBRARIAN"), manager = user("LIBRARY_MANAGER"), otherManager = user("LIBRARY_MANAGER"), reader = user("READER");
        Long book = jdbc.queryForObject("INSERT INTO books(title, author_id, category_id) VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id",
                Long.class, "S4013 " + UUID.randomUUID());
        Long copy = jdbc.queryForObject("INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition) VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD') RETURNING id",
                Long.class, book, "S4013-" + UUID.randomUUID());
        OffsetDateTime returned = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS), due = returned.minusDays(3), borrowed = returned.minusDays(8);
        Long loan = loans.insert(null, reader, staff, "S4013-" + UUID.randomUUID(), borrowed);
        loans.insertItem(loan, copy, borrowed, due);
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        jdbc.update("UPDATE loan_items SET returned_at = ?, returned_by = ?, returned_by_name = 'S4013 LIBRARIAN' WHERE id = ?", returned, staff, item);
        Long fee = fees.record(item, reader, new ReturnFeeAssessment("READY", "Phí đã ghi nhận.",
                "S4-01.1-OPEN-DAYS-HALF-UP-V1", due, returned, 3, 5000L, 15000L, 15000L, null,
                List.of(), List.of(), List.of())).feeId();
        Long request = service.create(fee, new CreateFeeReductionRequest(BigDecimal.valueOf(requested), "Hoàn cảnh khó khăn"), staff).id();
        return new Fixture(staff, manager, otherManager, reader, item, fee, request);
    }
    long waived(Fixture f) { return jdbc.queryForObject("SELECT waived_amount_vnd FROM reader_fees WHERE id = ?", Long.class, f.fee()); }
    String snapshot(Fixture f) { return jdbc.queryForObject("SELECT snapshot::text FROM return_fee_assessments WHERE item_id = ?", String.class, f.item()); }
    void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
    @Test void partialApprovalUpdatesDebtHistoryAndLibrarianDetailWithoutChangingOriginalAssessment() {
        var f = fixture(10000); var before = snapshot(f);
        var result = service.approve(f.request(), f.manager());
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.approvedAmountVnd()).isEqualTo(10000);
        assertThat(result.outstandingAmountVnd()).isEqualTo(5000);
        assertThat(result.decidedBy()).isEqualTo(f.manager()); assertThat(result.decidedAt()).isNotNull();
        assertThat(result.deciderName()).isEqualTo("S4013 LIBRARY_MANAGER");
        var detail = service.detail(f.fee(), f.staff());
        assertThat(detail.originalAmountVnd()).isEqualTo(15000); assertThat(detail.paidAmountVnd()).isZero();
        assertThat(detail.waivedAmountVnd()).isEqualTo(10000); assertThat(detail.outstandingAmountVnd()).isEqualTo(5000);
        assertThat(detail.pendingRequest()).isNull(); assertThat(detail.reductionRequests()).containsExactly(result);
        assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("5000");
        assertThat(snapshot(f)).isEqualTo(before);
    }
    @Test void fullWaiverRemovesOnlyReceivableAndRetainsFeeAndAssessmentHistory() {
        var f = fixture(15000); var before = snapshot(f);
        assertThat(service.approve(f.request(), f.manager()).outstandingAmountVnd()).isZero();
        assertThat(waived(f)).isEqualTo(15000); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isZero();
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM reader_fees WHERE id = ?", Long.class, f.fee())).isEqualTo(15000);
        assertThat(snapshot(f)).isEqualTo(before);
    }
    @Test void rejectionKeepsDebtAndStoresReasonVisibleToLibrarian() {
        var f = fixture(10000);
        var result = service.reject(f.request(), new RejectFeeReductionRequest(" Chưa đủ căn cứ "), f.manager());
        assertThat(result.status()).isEqualTo("REJECTED"); assertThat(result.approvedAmountVnd()).isZero();
        assertThat(result.decisionReason()).isEqualTo("Chưa đủ căn cứ"); assertThat(waived(f)).isZero();
        assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("15000");
        assertThat(service.detail(f.fee(), f.staff()).reductionRequests()).containsExactly(result);
    }
    @Test void allRepeatTransitionsFailAndOriginalDecisionIsUnchanged() {
        for (boolean approve : new boolean[]{true, false}) {
            var f = fixture(10000);
            var result = approve ? service.approve(f.request(), f.manager()) : service.reject(f.request(), new RejectFeeReductionRequest("Lý do"), f.manager());
            code(() -> service.approve(f.request(), f.otherManager()), "FEE_REDUCTION_PROCESSED");
            code(() -> service.reject(f.request(), new RejectFeeReductionRequest("Lý do khác"), f.otherManager()), "FEE_REDUCTION_PROCESSED");
            assertThat(service.detail(f.fee(), f.staff()).reductionRequests()).containsExactly(result);
        }
    }
    @Test void paymentDuringPendingBlocksApprovalButAllowsRejectionAtBothPartialAndFullPayment() {
        for (long paid : new long[]{1000, 15000}) {
            var f = fixture(10000); jdbc.update("UPDATE reader_fees SET paid_amount_vnd = ? WHERE id = ?", paid, f.fee());
            code(() -> service.approve(f.request(), f.manager()), "FEE_REDUCTION_PAID");
            assertThat(service.detail(f.fee(), f.staff()).pendingRequest()).isNotNull();
            var result = service.reject(f.request(), new RejectFeeReductionRequest("Đã thu tiền"), f.manager());
            assertThat(result.outstandingAmountVnd()).isEqualTo(15000 - paid); assertThat(waived(f)).isZero();
        }
    }
    @Test void concurrentManagersApproveExactlyOnce() throws Exception { race(false); }
    @Test void concurrentApprovalAndRejectionProduceOneConsistentDecision() throws Exception { race(true); }
    void race(boolean secondRejects) throws Exception {
        var f = fixture(10000); var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var first = pool.submit(() -> attempt(f, f.manager(), false, barrier));
            var second = pool.submit(() -> attempt(f, f.otherManager(), secondRejects, barrier));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "FEE_REDUCTION_PROCESSED");
            var detail = service.detail(f.fee(), f.staff()); assertThat(detail.reductionRequests()).hasSize(1);
            var decided = detail.reductionRequests().get(0);
            assertThat(detail.waivedAmountVnd()).isEqualTo(decided.approvedAmountVnd());
            assertThat(detail.outstandingAmountVnd()).isEqualTo(15000 - decided.approvedAmountVnd());
            assertThat(decided.decidedBy()).isIn(f.manager(), f.otherManager());
        } finally { pool.shutdownNow(); }
    }
    String attempt(Fixture f, Long manager, boolean reject, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { if (reject) service.reject(f.request(), new RejectFeeReductionRequest("Chưa đủ căn cứ"), manager);
              else service.approve(f.request(), manager); return "OK";
        } catch (ApiException e) { return e.getCode(); }
    }
    @Test void transactionRollbackRestoresPendingRequestAndReceivableTogether() {
        var f = fixture(10000); var template = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> template.execute(status -> {
            service.approve(f.request(), f.manager()); throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(waived(f)).isZero(); assertThat(service.detail(f.fee(), f.staff()).pendingRequest().status()).isEqualTo("PENDING");
    }
    @Test void databaseRejectsNonManagerForgedWaiverAndChangesToFinalHistory() {
        var f = fixture(10000);
        assertThatThrownBy(() -> jdbc.update("UPDATE fee_reduction_requests SET status = 'APPROVED', decided_by = ? WHERE id = ?", f.staff(), f.request())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE reader_fees SET waived_amount_vnd = 10000 WHERE id = ?", f.fee())).isInstanceOf(DataAccessException.class);
        var result = service.approve(f.request(), f.manager());
        assertThatThrownBy(() -> jdbc.update("UPDATE fee_reduction_requests SET status = 'REJECTED', decision_reason = 'Đổi ý' WHERE id = ?", f.request())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM fee_reduction_requests WHERE id = ?", f.request())).isInstanceOf(DataAccessException.class);
        assertThat(service.detail(f.fee(), f.staff()).reductionRequests()).containsExactly(result);
    }
    @Test void followupProposalCannotExceedRemainingDebtOrCreateNegativeReceivable() {
        var f = fixture(10000); service.approve(f.request(), f.manager());
        code(() -> service.create(f.fee(), new CreateFeeReductionRequest(BigDecimal.valueOf(10000), "Lý do"), f.staff()), "INVALID_REDUCTION_AMOUNT");
        var followup = service.create(f.fee(), new CreateFeeReductionRequest(BigDecimal.valueOf(5000), "Lý do"), f.staff());
        service.approve(followup.id(), f.manager());
        assertThat(waived(f)).isEqualTo(15000); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isZero();
    }
}
