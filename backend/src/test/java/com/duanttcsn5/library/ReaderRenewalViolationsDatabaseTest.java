package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in integration tests on a disposable PostgreSQL 15 database with existing Flyway migrations. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_05_4_DB_TEST", matches = "true")
@Transactional
class ReaderRenewalViolationsDatabaseTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LoanRepository repository;
    @Autowired private LoanService service;

    private record Fixture(Long reader, Long currentLoan, Long itemId, OffsetDateTime due) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Long reader = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Reader S3054 " + suffix, suffix + "@example.invalid");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, duration_months, max_books, loan_days,
                                       max_renewals, renewal_days, is_active)
                VALUES (?, 12, 5, 14, 3, 7, true) RETURNING id
                """, Long.class, "S3054-" + suffix);
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE + 365, 'ACTIVE')
                """, "CARD-" + suffix, reader, type);
        OffsetDateTime due = OffsetDateTime.now().plusDays(10);
        Long loan = createLoan(reader);
        Long item = createItem(loan, due);
        return new Fixture(reader, loan, item, due);
    }

    private Long createLoan(Long reader) {
        return repository.insert(null, reader, reader, "S3054-" + UUID.randomUUID(),
                OffsetDateTime.now().minusDays(5));
    }

    private Long createItem(Long loan, OffsetDateTime due) {
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3054-" + UUID.randomUUID());
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                                        received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD')
                RETURNING id
                """, Long.class, book, "BC-S3054-" + UUID.randomUUID());
        repository.insertItem(loan, copy, OffsetDateTime.now().minusDays(5), due);
        return jdbc.queryForObject("""
                SELECT id FROM loan_items WHERE loan_id = ? AND book_copy_id = ?
                """, Long.class, loan, copy);
    }

    private long count(Fixture fixture) {
        return jdbc.queryForObject("SELECT renewal_count FROM loans WHERE id = ?",
                Long.class, fixture.currentLoan());
    }

    private void unchanged(Fixture f) {
        assertThat(count(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.itemId()).toInstant()).isEqualTo(f.due().toInstant());
    }

    private void expectViolation(Fixture f, String... fragments) {
        assertThatThrownBy(() -> service.checkMyLoanRenewal(f.itemId(), f.reader()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_VIOLATIONS");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    for (String part : fragments) assertThat(e.getMessage()).contains(part);
                });
        unchanged(f);
    }

    @Test void otherOverdueLoanIsCountedOnlyWhileUnreturnedAndNotCurrentLoan() {
        Fixture f = fixture();
        Long oldLoan = createLoan(f.reader());
        Long overdueItem = createItem(oldLoan, OffsetDateTime.now().minusDays(3));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(repository.countOtherOverdueUnreturnedLoansForReader(
                f.reader(), f.currentLoan(), today)).isEqualTo(1);
        expectViolation(f, "1 phiếu mượn khác quá hạn");
        jdbc.update("UPDATE loan_items SET returned_at = CURRENT_TIMESTAMP WHERE id = ?", overdueItem);
        assertThat(repository.countOtherOverdueUnreturnedLoansForReader(
                f.reader(), f.currentLoan(), today)).isZero();
        assertThat(service.checkMyLoanRenewal(f.itemId(), f.reader()).eligible()).isTrue();
        assertThat(count(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.itemId()).toInstant()).isAfter(f.due().toInstant());
    }

    @Test void severalOverdueLoansCountOncePerLoanWhileIgnoringCurrentLoan() {
        Fixture f = fixture();
        Long oldLoan1 = createLoan(f.reader());
        createItem(oldLoan1, OffsetDateTime.now().minusDays(2));
        createItem(oldLoan1, OffsetDateTime.now().minusDays(3));
        Long oldLoan2 = createLoan(f.reader());
        createItem(oldLoan2, OffsetDateTime.now().minusDays(2));
        assertThat(repository.countOtherOverdueUnreturnedLoansForReader(f.reader(), f.currentLoan(),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))).isEqualTo(2);
        expectViolation(f, "2 phiếu mượn khác quá hạn");
    }

    @Test void currentLoanIsExcludedEvenIfAnotherCopyInThatLoanIsOverdue() {
        Fixture f = fixture();
        createItem(f.currentLoan(), OffsetDateTime.now().minusDays(3));
        assertThat(repository.countOtherOverdueUnreturnedLoansForReader(
                f.reader(), f.currentLoan(), LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))).isZero();
        assertThat(service.checkMyLoanRenewal(f.itemId(), f.reader()).eligible()).isTrue();
        assertThat(count(f)).isEqualTo(1);
    }

    @Test void remainingUnpaidFeeAndOverdueLoanAppearTogetherThenClearAfterPaymentAndReturn() {
        Fixture f = fixture();
        Long oldLoan = createLoan(f.reader());
        Long lateItem = createItem(oldLoan, OffsetDateTime.now().minusDays(2));
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd, paid_amount_vnd) VALUES (?, ?, ?)",
                f.reader(), 30000, 10000);
        assertThat(repository.sumUnpaidFeesForReader(f.reader()))
                .isEqualByComparingTo(new BigDecimal("20000"));
        expectViolation(f, "1 phiếu mượn khác quá hạn", "20.000", "nợ phí");
        jdbc.update("UPDATE loan_items SET returned_at = CURRENT_TIMESTAMP WHERE id = ?", lateItem);
        expectViolation(f, "nợ phí");
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd = amount_vnd WHERE reader_user_id = ?", f.reader());
        assertThat(repository.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.checkMyLoanRenewal(f.itemId(), f.reader()).eligible()).isTrue();
        assertThat(count(f)).isEqualTo(1);
    }

    @Test void noOtherViolationPermitsRenewalAndDoesNotCountOtherReadersDebt() {
        Fixture f = fixture();
        String suffix = UUID.randomUUID().toString();
        Long otherReader = jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Other reader S3054", suffix + "@example.invalid");
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd) VALUES (?, ?)",
                otherReader, 99000);
        assertThat(repository.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repository.countOtherOverdueUnreturnedLoansForReader(f.reader(), f.currentLoan(),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))).isZero();
        assertThat(service.checkMyLoanRenewal(f.itemId(), f.reader()).eligible()).isTrue();
        assertThat(count(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.itemId()).toInstant()).isAfter(f.due().toInstant());
    }
}
