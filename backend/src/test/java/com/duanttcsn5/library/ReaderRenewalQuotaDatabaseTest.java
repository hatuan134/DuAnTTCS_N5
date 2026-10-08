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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in PostgreSQL integration tests against a disposable database with Flyway V30.
 *  S3_05_3_DB_TEST=true enables this class; fixture data is rolled back after each test.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_05_3_DB_TEST", matches = "true")
@Transactional
class ReaderRenewalQuotaDatabaseTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LoanRepository repository;
    @Autowired private LoanService service;

    private record Fixture(Long loanId, Long firstItem, Long secondItem,
                           Long owner, Long bookId, OffsetDateTime due) {}

    private Long reader() {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "Reader S3053 " + suffix, suffix + "@example.invalid");
    }

    private Fixture fixture() {
        Long owner = reader();
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, duration_months, max_books, loan_days,
                                       max_renewals, renewal_days, is_active)
                VALUES (?, 12, 5, 14, 2, 7, true) RETURNING id
                """, Long.class, "S3053-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE + 365, 'ACTIVE')
                """, "CARD-S3053-" + UUID.randomUUID(), owner, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories))
                RETURNING id
                """, Long.class, "Book S3053-" + UUID.randomUUID());
        Long loan = repository.insert(null, owner, owner,
                "PM-S3053-" + UUID.randomUUID(), OffsetDateTime.now().minusDays(1));
        OffsetDateTime due = OffsetDateTime.now().plusDays(15);
        Long first = addItem(book, loan, due);
        Long second = addItem(book, loan, due);
        return new Fixture(loan, first, second, owner, book, due);
    }

    private Long addItem(Long book, Long loan, OffsetDateTime due) {
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                                        received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD')
                RETURNING id
                """, Long.class, book, "BC-S3053-" + UUID.randomUUID());
        repository.insertItem(loan, copy, OffsetDateTime.now().minusDays(1), due);
        return jdbc.queryForObject("""
                SELECT id FROM loan_items WHERE loan_id = ? AND book_copy_id = ?
                """, Long.class, loan, copy);
    }

    private int count(Long loan) {
        return jdbc.queryForObject("SELECT renewal_count FROM loans WHERE id = ?", Integer.class, loan);
    }

    @Test void twoItemsShareOneLoanQuotaAndCannotExceedIt() {
        Fixture f = fixture();
        assertThat(count(f.loanId())).isZero();
        assertThat(repository.findUnreturnedForReader(f.owner()))
                .allSatisfy(row -> {
                    assertThat(row.renewalsUsed()).isZero();
                    assertThat(row.maxRenewals()).isEqualTo(2);
                });

        assertThat(service.checkMyLoanRenewal(f.firstItem(), f.owner()).renewalsUsed()).isEqualTo(1);
        assertThat(service.checkMyLoanRenewal(f.secondItem(), f.owner()).renewalsUsed()).isEqualTo(2);
        assertThat(count(f.loanId())).isEqualTo(2);
        assertThat(repository.findUnreturnedForReader(f.owner()))
                .allSatisfy(row -> {
                    assertThat(row.renewalsUsed()).isEqualTo(2);
                    assertThat(row.maxRenewals()).isEqualTo(2);
                });
        assertThatThrownBy(() -> service.checkMyLoanRenewal(f.firstItem(), f.owner()))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RENEWAL_LIMIT_REACHED");
                    assertThat(error.getMessage()).contains("2/2");
                });
        assertThat(count(f.loanId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.firstItem()).toInstant()).isAfter(f.due().toInstant());
    }

    @Test void queueRejectionNeverIncrementsQuotaOrChangesDueDate() {
        Fixture f = fixture();
        Long other = reader();
        jdbc.update("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
                VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """, f.bookId(), other);
        assertThatThrownBy(() -> service.checkMyLoanRenewal(f.firstItem(), f.owner()))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_RESERVATION"));
        assertThat(count(f.loanId())).isZero();
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.firstItem()).toInstant()).isEqualTo(f.due().toInstant());
    }
}
