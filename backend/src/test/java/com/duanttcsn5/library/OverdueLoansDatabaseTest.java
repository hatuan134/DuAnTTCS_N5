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

import static org.assertj.core.api.Assertions.assertThat;

/** Disposable PostgreSQL only; fixture rows and trigger side effects roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_09_1_DB_TEST", matches = "true")
@Transactional
class OverdueLoansDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService loanService;

    private Long user(String role, String name, String phone) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, phone, status)
                SELECT id, ?, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, name, UUID.randomUUID() + "@example.invalid", phone, role);
    }

    private Long book(String title) {
        return jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, title);
    }

    private Long copy(Long bookId) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE, 50000, 'GOOD') RETURNING id
                """, Long.class, bookId, "S3091-" + UUID.randomUUID());
    }

    @Test
    void queryKeepsOnlyUnreturnedPastDueItemsAndOrdersEarlierDeadlineFirst() {
        Long staff = user("LIBRARIAN", "Thủ thư S3091", "0900000000");
        Long readerA = user("READER", "Nguyễn An", "0901111111");
        Long readerB = user("READER", "Trần Bình", "0902222222");
        Long bookA = book("S3091 Sách A");
        Long bookB = book("S3091 Sách B");
        OffsetDateTime borrowed = OffsetDateTime.parse("2026-10-01T09:00:00+07:00");
        OffsetDateTime todayStart = OffsetDateTime.parse("2026-10-09T00:00:00+07:00");

        Long loanOldest = loans.insert(null, readerA, staff, "S3091-OLD-" + UUID.randomUUID(), borrowed);
        Long oldestCopy = copy(bookA);
        loans.insertItem(loanOldest, oldestCopy, borrowed, OffsetDateTime.parse("2026-10-05T09:00:00+07:00"));

        Long loanTieEarly = loans.insert(null, readerB, staff, "S3091-TIE-EARLY-" + UUID.randomUUID(), borrowed);
        Long tieEarlyCopy = copy(bookB);
        loans.insertItem(loanTieEarly, tieEarlyCopy, borrowed, OffsetDateTime.parse("2026-10-07T08:00:00+07:00"));

        Long loanTieLate = loans.insert(null, readerA, staff, "S3091-TIE-LATE-" + UUID.randomUUID(), borrowed);
        Long tieLateCopy = copy(bookA);
        loans.insertItem(loanTieLate, tieLateCopy, borrowed, OffsetDateTime.parse("2026-10-07T16:00:00+07:00"));

        Long returnedLoan = loans.insert(null, readerA, staff, "S3091-RETURNED-" + UUID.randomUUID(), borrowed);
        Long returnedCopy = copy(bookB);
        loans.insertItem(returnedLoan, returnedCopy, borrowed, OffsetDateTime.parse("2026-10-04T09:00:00+07:00"));
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ?",
                OffsetDateTime.parse("2026-10-06T10:00:00+07:00"), returnedLoan);

        Long todayLoan = loans.insert(null, readerB, staff, "S3091-TODAY-" + UUID.randomUUID(), borrowed);
        loans.insertItem(todayLoan, copy(bookB), borrowed, OffsetDateTime.parse("2026-10-09T09:00:00+07:00"));

        Long futureLoan = loans.insert(null, readerB, staff, "S3091-FUTURE-" + UUID.randomUUID(), borrowed);
        loans.insertItem(futureLoan, copy(bookB), borrowed, OffsetDateTime.parse("2026-10-10T09:00:00+07:00"));

        var actual = loans.findOpenOverdue(todayStart);

        assertThat(actual).hasSize(3);
        assertThat(actual).extracting(LoanRepository.OverdueLoanRow::loanId)
                .containsExactly(loanOldest, loanTieEarly, loanTieLate);
        assertThat(actual.get(0).readerName()).isEqualTo("Nguyễn An");
        assertThat(actual.get(0).readerPhone()).isEqualTo("0901111111");
        assertThat(actual.get(0).bookTitle()).isEqualTo("S3091 Sách A");
        assertThat(actual).noneMatch(row -> row.loanId().equals(returnedLoan));
        assertThat(actual).noneMatch(row -> row.loanId().equals(todayLoan));
        assertThat(actual).noneMatch(row -> row.loanId().equals(futureLoan));
    }

    /** S3-07 + S3-09 end-to-end against disposable PostgreSQL with Flyway schema. */
    @Test
    void postgresReturnLookupAndOverdueListAgreeOnCalendarDays() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime dueAt = today.minusDays(3).atTime(17, 0).atZone(zone).toOffsetDateTime();
        OffsetDateTime borrowedAt = today.minusDays(10).atTime(9, 0).atZone(zone).toOffsetDateTime();
        Long staff = user("LIBRARIAN", "Thủ thư tích hợp", "0900000111");
        Long reader = user("READER", "Bạn đọc tích hợp", "0900000222");
        Long book = book("S309-REGRESSION-" + UUID.randomUUID());
        Long itemCopy = copy(book);
        String barcode = jdbc.queryForObject("SELECT barcode FROM book_copies WHERE id = ?", String.class, itemCopy);
        Long loanId = loans.insert(null, reader, staff, "S309-REG-" + UUID.randomUUID(), borrowedAt);
        loans.insertItem(loanId, itemCopy, borrowedAt, dueAt);

        var lookup = loanService.lookupReturn(barcode, staff);
        var matching = loanService.overdueLoans(staff).stream()
                .filter(item -> loanId.equals(item.loanId()))
                .toList();

        assertThat(lookup.status()).isEqualTo("OVERDUE");
        assertThat(lookup.overdueDays()).isEqualTo(3L);
        assertThat(matching).hasSize(1);
        assertThat(matching.get(0).overdueDays()).isEqualTo(lookup.overdueDays());
    }

}
