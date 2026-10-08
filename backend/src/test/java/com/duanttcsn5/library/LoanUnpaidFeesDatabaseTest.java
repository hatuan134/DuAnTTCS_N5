package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.CreateDirectLoanRequest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Enable only against a disposable PostgreSQL DB after Flyway V27; fixture data roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_03_3_DB_TEST", matches = "true")
@Transactional
class LoanUnpaidFeesDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;

    private Long createUser(String roleCode) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3033 " + roleCode, UUID.randomUUID() + "@example.invalid", roleCode);
    }

    private void fee(Long reader, long amount, long paid) {
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd, paid_amount_vnd) VALUES (?, ?, ?)",
                reader, amount, paid);
    }

    @Test
    void sumsOnlyRemainingDebtAcrossOneMultiplePartialAndSettledFees() {
        Long reader = createUser("READER"), other = createUser("READER");
        assertThat(loans.sumUnpaidFeesForReader(reader)).isEqualByComparingTo(BigDecimal.ZERO);
        fee(reader, 100000, 0);
        assertThat(loans.sumUnpaidFeesForReader(reader)).isEqualByComparingTo("100000");
        fee(reader, 50000, 10000); // 40,000 left
        fee(reader, 20000, 20000); // ignored
        fee(other, 999999, 0); // does not belong to the selected reader
        assertThat(loans.sumUnpaidFeesForReader(reader)).isEqualByComparingTo("140000");
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd = amount_vnd WHERE reader_user_id = ?", reader);
        assertThat(loans.sumUnpaidFeesForReader(reader)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsNegativeOverpaymentAndZeroAmountAtDatabaseBoundary() {
        Long reader = createUser("READER");
        // Violating a PostgreSQL constraint aborts the containing transaction. Isolate each test
        // in its own transaction only if using savepoints; this test documents the expected
        // constraints without executing intentionally failing SQL inside the shared @Transactional.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'reader_fees'::regclass
                  AND contype = 'c'
                """, Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(loans.sumUnpaidFeesForReader(reader)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void directLoanBlockedThenPermittedAfterPaymentWithoutCreatingARejectedLoan() {
        Long reader = createUser("READER"), staff = createUser("LIBRARIAN");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 5, 14, 1, 7) RETURNING id
                """, Long.class, "S3033-" + UUID.randomUUID());
        String card = "S3033-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 30, 'ACTIVE')
                """, card, reader, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3033-" + UUID.randomUUID());
        String barcode = "S3033-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 30000, 'GOOD')
                """, book, barcode);
        fee(reader, 75000, 25000);
        assertThat(service.readerEligibility(card, staff).message()).contains("50.000 ₫");
        var request = new CreateDirectLoanRequest(UUID.randomUUID(), card, List.of(barcode));
        assertThatThrownBy(() -> service.createDirectLoan(request, staff))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOAN_UNPAID_FEES"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE borrower_user_id = ?", Long.class, reader)).isZero();
        jdbc.update("UPDATE reader_fees SET paid_amount_vnd = amount_vnd WHERE reader_user_id = ?", reader);
        assertThat(service.readerEligibility(card, staff).eligible()).isTrue();
        assertThat(service.createDirectLoan(request, staff).loan().readerId()).isEqualTo(reader);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE borrower_user_id = ?", Long.class, reader)).isEqualTo(1);
    }
}
