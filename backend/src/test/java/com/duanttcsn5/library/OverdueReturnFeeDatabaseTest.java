package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ConfirmReturnRequest;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Dedicated disposable PostgreSQL ONLY. This suite configures the calendar and retains UUID history. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_01_1_DB_TEST", matches = "true")
class OverdueReturnFeeDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanService service;
    @Autowired LoanRepository loans;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    record Fixture(Long staff, Long otherStaff, Long reader, Long loan, Long item, Long copy, String barcode) {
        ConfirmReturnRequest request() { return new ConfirmReturnRequest(barcode, item); }
    }
    @BeforeEach void configure() {
        jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd = 5000 WHERE id = 1");
        jdbc.update("UPDATE library_weekly_schedule SET is_open = TRUE, open_time = '08:00', close_time = '17:00'");
        LocalDate today = LocalDate.now(ZONE);
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN ? AND ?", today.minusDays(10), today);
    }
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id",
                Long.class, "S4011 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture(boolean overdue) {
        Long staff = user("LIBRARIAN"), other = user("LIBRARIAN"), reader = user("READER");
        Long book = jdbc.queryForObject("INSERT INTO books(title, author_id, category_id) VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id",
                Long.class, "S4011 " + UUID.randomUUID());
        String barcode = "S4011-" + UUID.randomUUID();
        Long copy = jdbc.queryForObject("INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition) VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD') RETURNING id",
                Long.class, book, barcode);
        OffsetDateTime borrowed = OffsetDateTime.now(ZONE).minusDays(8).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime due = overdue ? borrowed.plusDays(3) : borrowed.plusDays(15);
        Long loan = loans.insert(null, reader, staff, "S4011-" + UUID.randomUUID(), borrowed);
        loans.insertItem(loan, copy, borrowed, due);
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        return new Fixture(staff, other, reader, loan, item, copy, barcode);
    }
    long feeCount(Fixture f) { return jdbc.queryForObject("SELECT COUNT(*) FROM reader_fees WHERE return_item_id = ?", Long.class, f.item()); }
    @Test void demoRecords15000AndBothStaffReadIdenticalFrozenSnapshotsAfterPriceAndCalendarChange() {
        var f = fixture(true); LocalDate today = LocalDate.now(ZONE);
        for (LocalDate date : List.of(today.minusDays(2), today.minusDays(1))) jdbc.update(
                "INSERT INTO library_closed_dates(closed_date, reason) VALUES (?, 'Ngày nghỉ demo')", date);
        assertThat(service.lookupReturn(f.barcode(), f.staff()).fee().amountVnd()).isEqualTo(15000L);
        var result = service.confirmReturn(f.request(), f.staff());
        assertThat(result.fee().overdueDays()).isEqualTo(3L);
        assertThat(result.fee().amountVnd()).isEqualTo(15000L); assertThat(feeCount(f)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM reader_fees WHERE return_item_id = ?", Long.class, f.item())).isEqualTo(15000L);
        jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd = 9000 WHERE id = 1");
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN ? AND ?", today.minusDays(5), today);
        var first = service.loanDetail(f.loan(), f.staff()).items().get(0).fee();
        var second = service.loanDetail(f.loan(), f.otherStaff()).items().get(0).fee();
        assertThat(first).isEqualTo(second).isEqualTo(result.fee());
        assertThat(first.dailyRateVnd()).isEqualTo(5000L);
        assertThatThrownBy(() -> service.confirmReturn(f.request(), f.otherStaff())).isInstanceOf(ApiException.class);
        assertThat(feeCount(f)).isEqualTo(1L);
    }
    @Test void onTimeReturnWithoutConfiguredPriceRecordsZeroAndCreatesNoReceivable() {
        var f = fixture(false); jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd = NULL WHERE id = 1");
        assertThat(service.confirmReturn(f.request(), f.staff()).fee().amountVnd()).isZero();
        assertThat(feeCount(f)).isZero();
        assertThat(service.loanDetail(f.loan(), f.otherStaff()).items().get(0).fee().amountVnd()).isZero();
    }
    @Test void unconfiguredOverduePriceBlocksReturnAndPreservesCopyAndLoan() {
        var f = fixture(true); jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd = NULL WHERE id = 1");
        assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("OVERDUE_FEE_NOT_CONFIGURED"));
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy())).isEqualTo("BORROWED");
        assertThat(loans.findReturnLookup(f.barcode()).orElseThrow().itemId()).isEqualTo(f.item());
        assertThat(feeCount(f)).isZero();
    }
    @Test void simultaneousReturnCreatesOnlyOneReceivable() throws Exception {
        var f = fixture(true); var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.confirmReturn(f.request(), f.staff()); return "OK"; }
                catch (ApiException e) { return e.getCode(); }
            };
            var a = pool.submit(attempt); var b = pool.submit(attempt);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "LOAN_ALREADY_RETURNED");
            assertThat(feeCount(f)).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }
    @Test void feeInsertFailureRollsBackReturnCopyAndAssessmentTogether() {
        var f = fixture(true); String constraint = "s4011_fail_" + f.reader();
        try {
            jdbc.execute("ALTER TABLE reader_fees ADD CONSTRAINT " + constraint + " CHECK (reader_user_id <> " + f.reader() + ")");
            assertThatThrownBy(() -> service.confirmReturn(f.request(), f.staff())).isInstanceOf(ApiException.class);
            assertThat(loans.findReturnLookup(f.barcode()).orElseThrow().itemId()).isEqualTo(f.item());
            assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, f.copy())).isEqualTo("BORROWED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM return_fee_assessments WHERE item_id = ?", Long.class, f.item())).isZero();
        } finally { jdbc.execute("ALTER TABLE reader_fees DROP CONSTRAINT IF EXISTS " + constraint); }
    }

    @Test void priceAtConfirmationOverridesPreviewAndRoundedZeroCreatesNoReceivable() {
        var f = fixture(true);
        assertThat(service.lookupReturn(f.barcode(), f.staff()).fee().dailyRateVnd()).isEqualTo(5000L);
        jdbc.update("UPDATE library_overdue_fee_settings SET daily_rate_vnd = 1 WHERE id = 1");
        var result = service.confirmReturn(f.request(), f.staff());
        assertThat(result.fee().dailyRateVnd()).isEqualTo(1L);
        assertThat(result.fee().rawAmountVnd()).isEqualTo(5L);
        assertThat(result.fee().amountVnd()).isZero(); assertThat(result.fee().feeId()).isNull();
        assertThat(feeCount(f)).isZero();
    }
    @Test void recordedAssessmentAndReceivableAmountCannotBeRepricedButPaymentColumnRemainsUsable() {
        var f = fixture(true); var result = service.confirmReturn(f.request(), f.staff());
        assertThatThrownBy(() -> jdbc.update("UPDATE reader_fees SET amount_vnd = amount_vnd + 1000 WHERE id = ?", result.fee().feeId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE return_fee_assessments SET snapshot = snapshot WHERE item_id = ?", f.item()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd = amount_vnd WHERE id = ?", result.fee().feeId());
        assertThat(service.loanDetail(f.loan(), f.staff()).items().get(0).fee()).isEqualTo(result.fee());
    }
}
