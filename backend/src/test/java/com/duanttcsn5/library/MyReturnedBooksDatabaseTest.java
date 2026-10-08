package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.MyReturnedBookResponse;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt in on a disposable PostgreSQL database with existing catalog fixtures; writes roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_04_3_DB_TEST", matches = "true")
@Transactional
class MyReturnedBooksDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;
    private static final OffsetDateTime BORROWED = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    private static final OffsetDateTime RETURNED = OffsetDateTime.parse("2026-10-08T10:00:00Z");
    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3043 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    private Long copy(Long book, String barcode) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, barcode);
    }
    private MyReturnedBookResponse returned(Long reader, Long staff, Long book, String title, OffsetDateTime at) {
        String suffix = UUID.randomUUID().toString(), barcode = "S3043-" + suffix, number = "PM-S3043-" + suffix;
        Long loan = loans.insert(null, reader, staff, number, BORROWED);
        loans.insertItem(loan, copy(book, barcode), BORROWED, RETURNED.plusDays(1));
        Long id = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE id = ?", at, id);
        return new MyReturnedBookResponse(id, title, barcode, number, BORROWED, at);
    }
    @ParameterizedTest @ValueSource(ints = {0, 7, 20, 21, 45})
    void stablePagesHaveNoDuplicatesGapsOrOtherReadersAndRespectPartialReturns(int count) {
        Long reader = user("READER"), other = user("READER"), staff = user("LIBRARIAN");
        String title = "S3043 " + UUID.randomUUID();
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, title);
        List<MyReturnedBookResponse> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) expected.add(returned(reader, staff, book, title,
                i == count - 1 ? RETURNED.minusDays(1) : RETURNED));
        returned(other, staff, book, title, RETURNED.plusDays(1));
        String suffix = UUID.randomUUID().toString();
        Long outstandingLoan = loans.insert(null, reader, staff, "PM-OPEN-" + suffix, BORROWED);
        loans.insertItem(outstandingLoan, copy(book, "OPEN-" + suffix), BORROWED, RETURNED);
        if (count > 0) {
            Long partialLoan = jdbc.queryForObject("SELECT loan_id FROM loan_items WHERE id = ?", Long.class, expected.get(0).id());
            loans.insertItem(partialLoan, copy(book, "PARTIAL-" + suffix), BORROWED, RETURNED);
        }
        expected.sort(Comparator.comparing(MyReturnedBookResponse::returnedAt).reversed()
                .thenComparing(Comparator.comparing(MyReturnedBookResponse::id).reversed()));
        var before = jdbc.queryForList("SELECT * FROM loan_items ORDER BY id");
        List<Long> ids = new ArrayList<>();
        int pages = Math.max(1, (count + 19) / 20);
        for (int page = 0; page < pages; page++) {
            var result = service.myReturnedBooks(reader, page);
            assertThat(result.total()).isEqualTo(count); assertThat(result.size()).isEqualTo(20);
            assertThat(result.items()).hasSize(Math.min(20, Math.max(0, count - page * 20)));
            for (var row : result.items()) {
                ids.add(row.id());
                var saved = expected.stream().filter(item -> item.id().equals(row.id())).findFirst().orElseThrow();
                assertThat(row.bookTitle()).isEqualTo(saved.bookTitle());
                assertThat(row.barcode()).isEqualTo(saved.barcode());
                assertThat(row.loanNumber()).isEqualTo(saved.loanNumber());
                assertThat(row.borrowedAt().toInstant()).isEqualTo(saved.borrowedAt().toInstant());
                assertThat(row.returnedAt().toInstant()).isEqualTo(saved.returnedAt().toInstant());
            }
            assertThat(service.myReturnedBooks(reader, page).items()).extracting(MyReturnedBookResponse::id)
                    .containsExactlyElementsOf(result.items().stream().map(MyReturnedBookResponse::id).toList());
        }
        assertThat(ids).doesNotHaveDuplicates().containsExactlyElementsOf(expected.stream().map(MyReturnedBookResponse::id).toList());
        assertThat(service.myReturnedBooks(reader, pages).items()).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM loan_items ORDER BY id")).isEqualTo(before);
    }
}
