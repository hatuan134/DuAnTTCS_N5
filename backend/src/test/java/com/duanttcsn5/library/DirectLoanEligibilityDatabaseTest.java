package com.duanttcsn5.library;

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

import static org.assertj.core.api.Assertions.*;

/** Opt in on a disposable PostgreSQL database; all fixture writes roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_02_1_DB_TEST", matches = "true")
@Transactional
class DirectLoanEligibilityDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;

    private Long user(String code) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3021 " + code, suffix + "@example.invalid", code);
    }

    private Long borrow(Long reader, Long staff, Long book) {
        String suffix = UUID.randomUUID().toString();
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3021-" + suffix);
        OffsetDateTime borrowed = OffsetDateTime.now().minusDays(7);
        Long loan = loans.insert(null, reader, staff, "S3021-" + suffix, borrowed);
        loans.insertItem(loan, copy, borrowed, OffsetDateTime.now().minusDays(1));
        return jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
    }

    @Test void countsCopiesAcrossLoansExcludesReturnedAndOtherReadersAndDoesNotWriteOnLookup() {
        Long reader = user("READER"), other = user("READER"), staff = user("LIBRARIAN");
        Long type = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 5, 14, 1, 7) RETURNING id
                """, Long.class, "S3021-" + UUID.randomUUID());
        String card = "S3021-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 30, 'ACTIVE')
                """, card, reader, type);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3021 " + UUID.randomUUID());
        borrow(reader, staff, book); borrow(reader, staff, book);
        Long returned = borrow(reader, staff, book);
        jdbc.update("UPDATE loan_items SET returned_at = clock_timestamp() WHERE id = ?", returned);
        borrow(other, staff, book);
        // Same title counts as two copies, overdue counts, returned and other reader do not count.
        assertThat(loans.countUnreturnedBooksForReader(reader)).isEqualTo(2L);
        var before = jdbc.queryForList("SELECT * FROM loans ORDER BY id");
        var items = jdbc.queryForList("SELECT * FROM loan_items ORDER BY id");
        var copies = jdbc.queryForList("SELECT * FROM book_copies ORDER BY id");
        var reservations = jdbc.queryForList("SELECT * FROM book_reservations ORDER BY id");
        var result = service.readerEligibility(card, staff);
        assertThat(result.borrowedBooks()).isEqualTo(2);
        assertThat(result.remainingBooks()).isEqualTo(3);
        assertThat(result.eligible()).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM loans ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM loan_items ORDER BY id")).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM book_copies ORDER BY id")).isEqualTo(copies);
        assertThat(jdbc.queryForList("SELECT * FROM book_reservations ORDER BY id")).isEqualTo(reservations);
    }
}
