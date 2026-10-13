package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.CollectFullFeesRequest;
import com.duanttcsn5.library.dto.loan.CollectPartialFeesRequest;
import com.duanttcsn5.library.dto.loan.FeeReceiptResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.FeePaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Enable only against a disposable PostgreSQL database: histories are intentionally retained. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_02_1_DB_TEST", matches = "true")
class FeePaymentDatabaseTest {
    @Autowired JdbcTemplate jdbc; @Autowired FeePaymentService service;
    @Autowired LoanRepository loans; @Autowired PlatformTransactionManager transactions;
    record Fixture(Long reader, Long staff, Long otherStaff) {}
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id",
                Long.class, "S4021 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture(long... amounts) {
        var f = new Fixture(user("READER"), user("LIBRARIAN"), user("LIBRARIAN"));
        for (long amount : amounts) jdbc.update("INSERT INTO reader_fees(reader_user_id, description, amount_vnd) VALUES (?, ?, ?)", f.reader(), "Phí demo " + amount, amount);
        return f;
    }
    CollectFullFeesRequest preview(Fixture f) {
        var d = service.detail(f.reader(), f.staff()); return new CollectFullFeesRequest(UUID.randomUUID(), d.totalOutstandingVnd(), d.debtToken());
    }
    void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
    long paymentCount(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM fee_payments WHERE reader_user_id = ?", Long.class, f.reader()); }
    @Test void oneAndThreeFeePaymentsClearDebtAndPersistEveryAllocation() {
        for (long[] amounts : new long[][]{{10000}, {10000, 15000, 5000}}) {
            var f = fixture(amounts); long total = java.util.Arrays.stream(amounts).sum();
            var saved = service.collect(f.reader(), preview(f), f.staff());
            assertThat(saved.totalAmountVnd()).isEqualTo(total); assertThat(saved.items()).hasSize(amounts.length);
            assertThat(saved.items().stream().mapToLong(FeeReceiptResponse.Item::collectedAmountVnd).sum()).isEqualTo(total);
            assertThat(saved.items().stream().mapToLong(FeeReceiptResponse.Item::collectedAmountVnd).toArray()).containsExactly(amounts);
            assertThat(loans.sumUnpaidFeesForReader(f.reader())).isZero(); assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isFalse();
            assertThat(paymentCount(f)).isEqualTo(1); assertThat(saved.collectorName()).isEqualTo("S4021 LIBRARIAN");
            assertThat(saved.collectedAt()).isNotNull(); assertThat(saved.receiptNumber()).startsWith("BL-");
        }
    }
    @Test void zeroDebtCannotCreatePayment() {
        var f = fixture(); var d = service.detail(f.reader(), f.staff());
        code(() -> service.collect(f.reader(), new CollectFullFeesRequest(UUID.randomUUID(), 1L, d.debtToken()), f.staff()), "NO_OUTSTANDING_FEES");
        assertThat(paymentCount(f)).isZero();
    }
    @Test void repeatedConfirmationReturnsSameReceiptAndNewKeyCannotChargeAgain() {
        var f = fixture(10000); var input = preview(f); var saved = service.collect(f.reader(), input, f.staff());
        assertThat(service.collect(f.reader(), input, f.staff())).isEqualTo(saved);
        code(() -> service.collect(f.reader(), new CollectFullFeesRequest(UUID.randomUUID(), input.expectedTotalVnd(), input.debtToken()), f.otherStaff()), "NO_OUTSTANDING_FEES");
        assertThat(paymentCount(f)).isEqualTo(1);
    }
    @Test void twoLibrariansCanCollectSameDebtOnlyOnce() throws Exception { race(false); }
    @Test void concurrentRepeatedRequestsReturnOneSameReceipt() throws Exception { race(true); }
    void race(boolean sameKey) throws Exception {
        var f = fixture(10000, 15000, 5000); var input = preview(f);
        var other = sameKey ? input : new CollectFullFeesRequest(UUID.randomUUID(), input.expectedTotalVnd(), input.debtToken());
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var a = pool.submit(() -> attempt(f, input, f.staff(), barrier));
            var b = pool.submit(() -> attempt(f, other, sameKey ? f.staff() : f.otherStaff(), barrier));
            var outcomes = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            if (sameKey) assertThat(outcomes).containsExactly("OK", "OK");
            else assertThat(outcomes).containsExactlyInAnyOrder("OK", "NO_OUTSTANDING_FEES");
            assertThat(paymentCount(f)).isEqualTo(1); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isZero();
        } finally { pool.shutdownNow(); }
    }
    String attempt(Fixture f, CollectFullFeesRequest input, Long actor, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { service.collect(f.reader(), input, actor); return "OK"; } catch (ApiException e) { return e.getCode(); }
    }
    @Test void newFeeOrChangedComponentAfterPreviewRequiresNewConfirmation() {
        var f = fixture(10000); var input = preview(f);
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd) VALUES (?, 5000)", f.reader());
        code(() -> service.collect(f.reader(), input, f.staff()), "FEE_DEBT_CHANGED"); assertThat(paymentCount(f)).isZero();
        assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("15000");
    }
    @Test void transactionRollbackRestoresBalanceAndDeletesPaymentAndItems() {
        var f = fixture(10000, 15000, 5000); var input = preview(f);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            service.collect(f.reader(), input, f.staff()); throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(paymentCount(f)).isZero(); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("30000");
        assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
    }
    @Test void failingSecondFeeUpdateRollsBackFirstFeeAndReceipt() {
        var f = fixture(10000, 15000, 5000); var input = preview(f);
        Long fee = jdbc.queryForObject("SELECT id FROM reader_fees WHERE reader_user_id = ? ORDER BY id LIMIT 1 OFFSET 1", Long.class, f.reader());
        String name = "s4021_fail_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.id = " + fee
                + " THEN RAISE EXCEPTION 'forced test failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE UPDATE ON reader_fees FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try {
            code(() -> service.collect(f.reader(), input, f.staff()), "FEE_PAYMENT_FAILED");
            assertThat(paymentCount(f)).isZero(); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("30000");
            assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
        } finally {
            jdbc.execute("DROP TRIGGER " + name + " ON reader_fees"); jdbc.execute("DROP FUNCTION " + name + "()");
        }
    }
    @Test void receiptNumbersAreUniqueAndReprintingReadsStoredNamesAndAmounts() {
        var f = fixture(10000); var g = fixture(15000);
        var a = service.collect(f.reader(), preview(f), f.staff()); var b = service.collect(g.reader(), preview(g), g.staff());
        assertThat(a.receiptNumber()).isNotEqualTo(b.receiptNumber());
        jdbc.update("UPDATE users SET full_name = 'Đổi tên sau khi thu' WHERE id IN (?, ?)", f.reader(), f.staff());
        assertThat(service.receipt(a.id(), f.otherStaff())).isEqualTo(a);
        assertThat(service.receipt(a.id(), f.otherStaff())).isEqualTo(a); assertThat(paymentCount(f)).isEqualTo(1);
    }
    @Test void printedHistoryCannotBeEditedOrDeleted() {
        var f = fixture(10000); var a = service.collect(f.reader(), preview(f), f.staff());
        assertThatThrownBy(() -> jdbc.update("UPDATE fee_payments SET total_amount_vnd = 1 WHERE id = ?", a.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM fee_payment_items WHERE payment_id = ?", a.id())).isInstanceOf(DataAccessException.class);
        assertThat(service.receipt(a.id(), f.staff())).isEqualTo(a);
    }
    @Test void otherRolesCannotCollectAndCardRestrictionsAreNotCleared() {
        var f = fixture(10000); var input = preview(f);
        for (String role : new String[]{"ADMIN", "LIBRARY_MANAGER", "READER"}) code(() -> service.collect(f.reader(), input, user(role)), "FEE_PAYMENT_ROLE_REQUIRED");
        Long type = jdbc.queryForObject("INSERT INTO card_types(name,max_books,loan_days,max_renewals,renewal_days) VALUES (?,3,14,1,7) RETURNING id", Long.class, "S4021-" + UUID.randomUUID());
        jdbc.update("INSERT INTO library_cards(card_number,user_id,card_type_id,expires_at,status) VALUES (?,?,?,CURRENT_DATE + 30,'LOCKED')", "S4021-" + UUID.randomUUID(), f.reader(), type);
        service.collect(f.reader(), input, f.staff());
        var d = service.detail(f.reader(), f.staff()); assertThat(d.hasDebt()).isFalse(); assertThat(d.reader().cardStatus()).isEqualTo("LOCKED");
    }

    CollectPartialFeesRequest partialPreview(Fixture f, long amount) {
        var d = service.detail(f.reader(), f.staff());
        return new CollectPartialFeesRequest(UUID.randomUUID(), d.totalOutstandingVnd(), d.debtToken(), amount);
    }
    @Test void partialDemoThreeCollectionsPreserveBalancesReceiptsAndClearDebtAtLastPayment() {
        var f = fixture(10000, 15000, 5000);
        var a = service.collectPartial(f.reader(), partialPreview(f, 10000), f.staff());
        assertThat(a.debtBeforeVnd()).isEqualTo(30000); assertThat(a.debtAfterVnd()).isEqualTo(20000);
        assertThat(a.items()).hasSize(1); assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
        var b = service.collectPartial(f.reader(), partialPreview(f, 15000), f.staff());
        assertThat(b.debtBeforeVnd()).isEqualTo(20000); assertThat(b.debtAfterVnd()).isEqualTo(5000);
        assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
        var c = service.collectPartial(f.reader(), partialPreview(f, 5000), f.staff());
        assertThat(c.debtAfterVnd()).isZero(); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isZero();
        var d = service.detail(f.reader(), f.staff()); assertThat(d.hasDebt()).isFalse(); assertThat(d.fees()).isEmpty();
        assertThat(d.payments().stream().mapToLong(p -> p.debtAfterVnd()).toArray()).containsExactly(0, 5000, 20000);
        assertThat(paymentCount(f)).isEqualTo(3);
        for (var receipt : List.of(a, b, c)) {
            assertThat(receipt.items().stream().mapToLong(FeeReceiptResponse.Item::collectedAmountVnd).sum()).isEqualTo(receipt.totalAmountVnd());
            assertThat(service.receipt(receipt.id(), f.otherStaff())).isEqualTo(receipt);
        }
        assertThat(List.of(a.receiptNumber(), b.receiptNumber(), c.receiptNumber())).doesNotHaveDuplicates();
        code(() -> service.collectPartial(f.reader(), new CollectPartialFeesRequest(UUID.randomUUID(), 1L, d.debtToken(), 1L), f.staff()), "NO_OUTSTANDING_FEES");
    }
    @Test void onePartialFeeThenCollectionAcrossTwoFeesKeepsOnlyActualAllocationsOnReceipt() {
        var f = fixture(10000, 15000, 5000);
        var a = service.collectPartial(f.reader(), partialPreview(f, 4000), f.staff());
        assertThat(a.items()).hasSize(1); assertThat(a.items().get(0).collectedAmountVnd()).isEqualTo(4000);
        var b = service.collectPartial(f.reader(), partialPreview(f, 10000), f.staff());
        assertThat(b.items().stream().mapToLong(FeeReceiptResponse.Item::collectedAmountVnd).toArray()).containsExactly(6000, 4000);
        assertThat(b.items().get(0).previouslyPaidVnd()).isEqualTo(4000);
        assertThat(service.detail(f.reader(), f.staff()).totalOutstandingVnd()).isEqualTo(16000);
        var c = service.collect(f.reader(), preview(f), f.staff());
        assertThat(c.totalAmountVnd()).isEqualTo(16000); assertThat(c.paymentMode()).isEqualTo("FULL");
        assertThat(c.debtAfterVnd()).isZero(); assertThat(service.receipt(a.id(), f.staff())).isEqualTo(a);
    }
    @Test void partialConcurrentCollectorsRejectStaleBalancesAndRepeatedKeyReplays() throws Exception {
        partialRace(false); partialRace(true);
    }
    void partialRace(boolean sameKey) throws Exception {
        var f = fixture(10000, 15000, 5000); var input = partialPreview(f, 10000);
        var other = sameKey ? input : new CollectPartialFeesRequest(UUID.randomUUID(), input.expectedTotalVnd(), input.debtToken(), input.amountVnd());
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var a = pool.submit(() -> partialAttempt(f, input, f.staff(), barrier));
            var b = pool.submit(() -> partialAttempt(f, other, sameKey ? f.staff() : f.otherStaff(), barrier));
            var outcomes = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            if (sameKey) assertThat(outcomes).containsExactly("OK", "OK");
            else assertThat(outcomes).containsExactlyInAnyOrder("OK", "FEE_DEBT_CHANGED");
            assertThat(paymentCount(f)).isEqualTo(1); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("20000");
            assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
        } finally { pool.shutdownNow(); }
    }
    String partialAttempt(Fixture f, CollectPartialFeesRequest input, Long actor, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { service.collectPartial(f.reader(), input, actor); return "OK"; } catch (ApiException e) { return e.getCode(); }
    }
    @Test void failureWhileAllocatingSecondPartialFeeRollsBackMoneyHistoryAndItems() {
        var f = fixture(10000, 15000, 5000); var input = partialPreview(f, 20000);
        Long fee = jdbc.queryForObject("SELECT id FROM reader_fees WHERE reader_user_id = ? ORDER BY id LIMIT 1 OFFSET 1", Long.class, f.reader());
        String name = "s4022_fail_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.id = " + fee
                + " THEN RAISE EXCEPTION 'forced partial test failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE UPDATE ON reader_fees FOR EACH ROW EXECUTE FUNCTION " + name + "()");
        try {
            code(() -> service.collectPartial(f.reader(), input, f.staff()), "FEE_PAYMENT_FAILED");
            assertThat(paymentCount(f)).isZero(); assertThat(loans.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("30000");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fee_payment_items i JOIN reader_fees f ON f.id=i.reader_fee_id WHERE f.reader_user_id=?", Long.class, f.reader())).isZero();
            assertThat(service.detail(f.reader(), f.staff()).hasDebt()).isTrue();
        } finally { jdbc.execute("DROP TRIGGER " + name + " ON reader_fees"); jdbc.execute("DROP FUNCTION " + name + "()"); }
    }
    @Test void partialRepeatedPayloadCannotBeChangedAndReceiptDoesNotChangeOnLaterCollections() {
        var f = fixture(30000); var input = partialPreview(f, 10000);
        var saved = service.collectPartial(f.reader(), input, f.staff());
        assertThat(service.collectPartial(f.reader(), input, f.staff())).isEqualTo(saved);
        code(() -> service.collectPartial(f.reader(), new CollectPartialFeesRequest(input.requestKey(), input.expectedTotalVnd(), input.debtToken(), java.math.BigDecimal.valueOf(5000)), f.staff()), "PAYMENT_REQUEST_REUSED");
        code(() -> service.collectPartial(f.reader(), input, f.otherStaff()), "PAYMENT_REQUEST_REUSED");
        service.collectPartial(f.reader(), partialPreview(f, 15000), f.otherStaff());
        assertThat(service.receipt(saved.id(), f.staff())).isEqualTo(saved); assertThat(saved.debtAfterVnd()).isEqualTo(20000);
        assertThat(paymentCount(f)).isEqualTo(2);
    }
}
