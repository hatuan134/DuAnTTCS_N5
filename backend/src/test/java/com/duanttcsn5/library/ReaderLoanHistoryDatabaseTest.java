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

import static org.assertj.core.api.Assertions.assertThat;

/** Run only against a disposable PostgreSQL database; all fixture rows roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_10_1_DB_TEST", matches = "true")
@Transactional
class ReaderLoanHistoryDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;

    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, 'S3101 Test', ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.invalid", role);
    }

    private Long copy(Long bookId) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE, 50000, 'GOOD') RETURNING id
                """, Long.class, bookId, "S3101-" + UUID.randomUUID());
    }

    @Test
    void queryScopesReaderKeepsEveryItemAndEmptyLoanAndOrdersDatesThenIdsDescending() {
        Long staff = user("LIBRARIAN"), reader = user("READER"), other = user("READER");
        assertThat(loans.findReaderHistory(reader)).isEmpty();
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES ('S3101 Sách mẫu', (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class);
        OffsetDateTime older = OffsetDateTime.parse("2026-10-01T09:00:00+07:00");
        OffsetDateTime newer = OffsetDateTime.parse("2026-10-03T09:00:00+07:00");
        OffsetDateTime due = OffsetDateTime.parse("2026-10-07T09:00:00+07:00");
        OffsetDateTime returned = OffsetDateTime.parse("2026-10-08T09:00:00+07:00");
        Long oldest = loans.insert(null, reader, staff, "S3101-OLD-" + UUID.randomUUID(), older);
        Long empty = loans.insert(null, reader, staff, "S3101-EMPTY-" + UUID.randomUUID(), newer);
        Long latest = loans.insert(null, reader, staff, "S3101-NEW-" + UUID.randomUUID(), newer);
        Long unrelated = loans.insert(null, other, staff, "S3101-OTHER-" + UUID.randomUUID(), newer.plusDays(1));
        loans.insertItem(oldest, copy(book), older, null); // legacy deadline
        loans.insertItem(unrelated, copy(book), newer, due);
        Long copyA = copy(book), copyB = copy(book);
        loans.insertItem(latest, copyA, newer, due);
        loans.insertItem(latest, copyB, newer, due);
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ? AND book_copy_id = ?", returned, latest, copyA);

        var rows = loans.findReaderHistory(reader);
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(LoanRepository.ReaderHistoryRow::loanId)
                .containsExactly(latest, latest, empty, oldest).doesNotContain(unrelated);
        assertThat(rows.get(0).itemId()).isLessThan(rows.get(1).itemId());
        assertThat(rows.get(0).bookTitle()).isEqualTo("S3101 Sách mẫu");
        assertThat(rows.get(0).barcode()).startsWith("S3101-");
        assertThat(rows.get(0).dueAt().toInstant()).isEqualTo(due.toInstant());
        assertThat(rows.get(0).returnedAt().toInstant()).isEqualTo(returned.toInstant());
        assertThat(rows.get(1).returnedAt()).isNull();
        assertThat(rows.get(2).itemId()).isNull();
        assertThat(rows.get(3).dueAt()).isNull();
    }
}
