package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
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
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real owner-filtered SQL and services on a disposable PostgreSQL database; fixtures roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_04_4_DB_TEST", matches = "true")
@Transactional
class ReaderLoanOwnershipDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    private Long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3044 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    private Long borrow(Long reader, Long staff, Long book, boolean returned) {
        String suffix = UUID.randomUUID().toString();
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, book, "S3044-" + suffix);
        Long loan = loans.insert(null, reader, staff, "PM-S3044-" + suffix, AT);
        loans.insertItem(loan, copy, AT, AT.plusDays(14));
        if (returned) jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ?", AT.plusDays(7), loan);
        return loan;
    }
    private void denied(Long loan, Long reader) {
        assertThatThrownBy(() -> service.loanDetail(loan, reader)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(404);
            assertThat(e.getCode()).isEqualTo("LOAN_NOT_FOUND");
            assertThat(e.getMessage()).isEqualTo("Phiếu không tồn tại hoặc bạn không có quyền truy cập.");
            assertThat(e.getDetails()).isEmpty();
        });
    }
    @Test void twoAccountsHaveDisjointListsHistoryPagesAndDetailsWithoutMutatingLoans() {
        Long a = user("READER"), b = user("READER"), staff = user("LIBRARIAN");
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3044 " + UUID.randomUUID());
        Long aOpen = borrow(a, staff, book, false), bOpen = borrow(b, staff, book, false);
        for (int i = 0; i < 21; i++) { borrow(a, staff, book, true); borrow(b, staff, book, true); }
        var before = jdbc.queryForList("SELECT * FROM loan_items ORDER BY id");
        var aDetail = service.loanDetail(aOpen, a);
        var bDetail = service.loanDetail(bOpen, b);
        assertThat(aDetail.readerId()).isEqualTo(a); assertThat(bDetail.readerId()).isEqualTo(b);
        assertThat(service.myBorrowedBooks(a)).extracting(MyBorrowedBookResponse::id)
                .containsExactly(aDetail.items().get(0).id());
        assertThat(service.myBorrowedBooks(b)).extracting(MyBorrowedBookResponse::id)
                .containsExactly(bDetail.items().get(0).id());
        assertThat(loans.findHeaderForReader(bOpen, a)).isEmpty();
        assertThat(loans.findHeaderForReader(aOpen, b)).isEmpty();
        denied(bOpen, a); denied(aOpen, b); denied(Long.MAX_VALUE, a);
        Set<Long> aIds = new HashSet<>(), bIds = new HashSet<>();
        for (Long reader : List.of(a, b)) {
            Set<Long> ids = reader.equals(a) ? aIds : bIds;
            for (int page : new int[]{0, 1}) {
                var history = service.myReturnedBooks(reader, page);
                assertThat(history.total()).isEqualTo(21);
                assertThat(history.items()).hasSize(page == 0 ? 20 : 1);
                for (var row : history.items()) {
                    assertThat(ids.add(row.id())).isTrue();
                    Long owner = jdbc.queryForObject("""
                            SELECT l.borrower_user_id FROM loan_items li JOIN loans l ON l.id = li.loan_id WHERE li.id = ?
                            """, Long.class, row.id());
                    assertThat(owner).isEqualTo(reader);
                    Long loan = jdbc.queryForObject("SELECT loan_id FROM loan_items WHERE id = ?", Long.class, row.id());
                    assertThat(service.loanDetail(loan, reader).loanNumber()).isEqualTo(row.loanNumber());
                    denied(loan, reader.equals(a) ? b : a);
                }
            }
            assertThat(ids).hasSize(21);
        }
        assertThat(Collections.disjoint(aIds, bIds)).isTrue();
        assertThat(service.loanDetail(aOpen, staff).readerId()).isEqualTo(a);
        assertThat(service.loanDetail(bOpen, staff).readerId()).isEqualTo(b);
        assertThat(jdbc.queryForList("SELECT * FROM loan_items ORDER BY id")).isEqualTo(before);
    }
}
