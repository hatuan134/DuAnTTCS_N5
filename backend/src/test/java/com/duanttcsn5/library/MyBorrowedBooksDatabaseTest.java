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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Opt in on a disposable PostgreSQL database; all fixture writes roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_04_1_DB_TEST", matches = "true")
@Transactional
class MyBorrowedBooksDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;

    private Long user(String code) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3041 " + code, suffix + "@example.invalid", code);
    }

    private Long borrow(Long reader, Long staff, Long book) {
        String suffix = UUID.randomUUID().toString();
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3041-" + suffix);
        OffsetDateTime borrowed = OffsetDateTime.now().minusDays(7);
        Long loan = loans.insert(null, reader, staff, "S3041-" + suffix, borrowed);
        loans.insertItem(loan, copy, borrowed, OffsetDateTime.now().minusDays(1));
        return jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
    }

    @Test void listsOnlyOutstandingCopiesOfCurrentReaderAndKeepsPartiallyReturnedLoan() {
        Long reader = user("READER"), other = user("READER"), empty = user("READER"), staff = user("LIBRARIAN");
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3041 " + UUID.randomUUID());
        Long first = borrow(reader, staff, book);
        Long second = borrow(reader, staff, book);
        Long returned = borrow(reader, staff, book);
        Long firstLoan = jdbc.queryForObject("SELECT loan_id FROM loan_items WHERE id = ?", Long.class, first);
        String suffix = UUID.randomUUID().toString();
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3041-" + suffix);
        loans.insertItem(firstLoan, copy, OffsetDateTime.now().minusDays(5), null);
        jdbc.update("UPDATE loan_items SET returned_at = clock_timestamp() WHERE loan_id = ? AND book_copy_id = ?", firstLoan, copy);
        jdbc.update("UPDATE loan_items SET returned_at = clock_timestamp() WHERE id = ?", returned);
        borrow(other, staff, book);
        var before = jdbc.queryForList("SELECT * FROM loan_items ORDER BY id");
        var actual = loans.findUnreturnedForReader(reader);
        assertThat(actual).extracting(r -> r.id()).containsExactly(second, first);
        assertThat(actual).allSatisfy(row -> {
            assertThat(row.bookTitle()).startsWith("S3041 ");
            assertThat(row.barcode()).startsWith("S3041-");
            assertThat(row.borrowedAt()).isNotNull();
            assertThat(row.dueAt()).isNotNull();
        });
        assertThat(loans.findUnreturnedForReader(empty)).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM loan_items ORDER BY id")).isEqualTo(before);
    }
}
