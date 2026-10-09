package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Disposable PostgreSQL only; fixture rows and trigger side effects roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_07_1_DB_TEST", matches = "true")
@Transactional
class ReturnLookupDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    Long user(String role, String name) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, name, UUID.randomUUID() + "@example.invalid", role);
    }
    Long copy(Long book, String barcode) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE, 50000, 'GOOD') RETURNING id
                """, Long.class, book, barcode);
    }
    @Test void lookupUsesCurrentUnreturnedItemAndKeepsNoLoanCopiesDistinctFromUnknown() {
        Long staff = user("LIBRARIAN", "Thủ thư kiểm thử"), oldReader = user("READER", "Bạn đọc cũ"),
                currentReader = user("READER", "Bạn đọc hiện tại");
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES ('S3071 Mắt biếc', (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class);
        String barcode = "S3071-" + UUID.randomUUID(); Long copy = copy(book, barcode);
        OffsetDateTime borrowed = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusDays(7), due = borrowed.plusDays(3);
        Long oldLoan = loans.insert(null, oldReader, staff, "OLD-" + UUID.randomUUID(), borrowed);
        loans.insertItem(oldLoan, copy, borrowed, due);
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ?", borrowed.plusDays(2), oldLoan);
        var unborrowed = loans.findReturnLookup(barcode).orElseThrow();
        assertThat(unborrowed.itemId()).isNull(); assertThat(unborrowed.readerId()).isNull();
        Long currentLoan = loans.insert(null, currentReader, staff, "NEW-" + UUID.randomUUID(), borrowed.plusDays(3));
        loans.insertItem(currentLoan, copy, borrowed.plusDays(3), due.plusDays(7));
        // Partially returned loan: a returned sibling must not affect this copy.
        String siblingBarcode = "S3071-" + UUID.randomUUID(); Long sibling = copy(book, siblingBarcode);
        loans.insertItem(currentLoan, sibling, borrowed.plusDays(3), due.plusDays(7));
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE book_copy_id = ?", borrowed.plusDays(4), sibling);
        var actual = loans.findReturnLookup(barcode).orElseThrow();
        assertThat(actual.readerId()).isEqualTo(currentReader);
        assertThat(actual.readerName()).isEqualTo("Bạn đọc hiện tại");
        assertThat(actual.bookTitle()).isEqualTo("S3071 Mắt biếc");
        assertThat(actual.loanId()).isEqualTo(currentLoan);
        assertThat(actual.borrowedAt()).isEqualTo(borrowed.plusDays(3));
        assertThat(actual.dueAt()).isEqualTo(due.plusDays(7));
        assertThat(loans.findReturnLookup(siblingBarcode).orElseThrow().itemId()).isNull();
        assertThat(loans.findReturnLookup("UNKNOWN-" + UUID.randomUUID())).isEmpty();
        String availableBarcode = "S3071-" + UUID.randomUUID(); copy(book, availableBarcode);
        assertThat(loans.findReturnLookup(availableBarcode).orElseThrow().loanId()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, copy)).isEqualTo("BORROWED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loan_items WHERE book_copy_id = ? AND returned_at IS NULL", Long.class, copy)).isEqualTo(1L);
    }
}
