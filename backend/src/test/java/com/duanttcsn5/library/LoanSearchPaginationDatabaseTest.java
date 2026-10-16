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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Opt-in PostgreSQL integration tests; use a disposable database. Fixtures roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_08_2_DB_TEST", matches = "true")
@Transactional
class LoanSearchPaginationDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LoanRepository loans;
    @Autowired LoanService service;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-01T08:00:00+07:00");

    private Long user(String role) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S3082 " + role, suffix + "@example.invalid", role);
    }

    private record Fixture(Long reader, Long staff, Long book, String card, String prefix) {}

    private Fixture fixture() {
        Long reader = user("READER");
        Long staff = user("LIBRARIAN");
        String prefix = "S3082-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        Long cardType = jdbc.queryForObject("""
                INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
                VALUES (?, 10, 14, 1, 7) RETURNING id
                """, Long.class, prefix + "thẻ");
        String card = prefix + "CARD";
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - 1, CURRENT_DATE + 30, 'ACTIVE')
                """, card, reader, cardType);
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, prefix + "Sách mẫu");
        return new Fixture(reader, staff, book, card, prefix);
    }

    private Long add(Fixture f, int index, boolean returned, OffsetDateTime time) {
        String barcode = f.prefix() + "COPY-" + index;
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, f.book(), barcode);
        Long loan = loans.insert(null, f.reader(), f.staff(), f.prefix() + "PM-" + index, time);
        loans.insertItem(loan, copy, time, time.plusDays(14));
        if (returned) {
            jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ?", time.plusDays(2), loan);
        }
        return loan;
    }

    @Test
    void emptyNineteenTwentyTwentyOneAndTwentySevenHaveNoMissingOrDuplicatedLoans() {
        var f = fixture();
        assertThat(service.searchLoans(f.card(), 0, f.staff()).total()).isZero();
        assertThat(service.searchLoans(f.card(), 0, f.staff()).items()).isEmpty();
        List<Long> created = new ArrayList<>();
        for (int index = 0; index < 27; index++) {
            // All returned loans are deliberately newer than some open loans.
            // The latest borrowed timestamp also ties every third loan; id must break ties.
            OffsetDateTime time = START.minusMinutes(index / 3);
            created.add(add(f, index, index < 10, time));
            if (index == 18 || index == 19 || index == 20 || index == 26) {
                int count = index + 1;
                var first = service.searchLoans(f.card(), 0, f.staff());
                assertThat(first.total()).isEqualTo(count);
                assertThat(first.items()).hasSize(Math.min(20, count));
                if (count > 20) {
                    var second = service.searchLoans(f.card(), 1, f.staff());
                    assertThat(second.total()).isEqualTo(count);
                    assertThat(second.items()).hasSize(count - 20);
                    var ids = new HashSet<Long>();
                    first.items().forEach(item -> assertThat(ids.add(item.id())).isTrue());
                    second.items().forEach(item -> assertThat(ids.add(item.id())).isTrue());
                    assertThat(ids).containsExactlyInAnyOrderElementsOf(created);
                    assertThat(first.items()).extracting(r -> r.status())
                            .doesNotContainNull();
                }
            }
        }
        var first = service.searchLoans(f.card(), 0, f.staff());
        var second = service.searchLoans(f.card(), 1, f.staff());
        var all = new ArrayList<>(first.items());
        all.addAll(second.items());
        assertThat(all.subList(0, 17)).allSatisfy(item -> assertThat(item.status()).isEqualTo("BORROWED"));
        assertThat(all.subList(17, 27)).allSatisfy(item -> assertThat(item.status()).isEqualTo("RETURNED"));
        assertThat(all.subList(0, 17)).extracting(r -> r.id())
                .containsExactlyElementsOf(List.of(created.get(26), created.get(25), created.get(24),
                        created.get(23), created.get(22), created.get(21), created.get(20),
                        created.get(19), created.get(18), created.get(17), created.get(16),
                        created.get(15), created.get(14), created.get(13), created.get(12),
                        created.get(11), created.get(10)));
        assertThat(service.searchLoans(f.card(), 2, f.staff()).items()).isEmpty();
    }

    @Test
    void multipleItemsForLoanNeverInflateCountAndBarcodeCanFindHistoricalLoan() {
        var f = fixture();
        Long firstLoan = add(f, 1, false, START);
        Long secondLoan = add(f, 2, true, START.plusMinutes(1));
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD') RETURNING id
                """, Long.class, f.book(), f.prefix() + "EXTRA");
        loans.insertItem(firstLoan, copy, START, START.plusDays(14));
        jdbc.update("UPDATE loan_items SET returned_at = ? WHERE loan_id = ? AND book_copy_id = ?",
                START.plusDays(2), firstLoan, copy);
        assertThat(service.searchLoans(f.card(), 0, f.staff()).total()).isEqualTo(2L);
        var all = service.searchLoans(f.card(), 0, f.staff()).items();
        assertThat(all).extracting(r -> r.id()).containsExactly(firstLoan, secondLoan);
        assertThat(all.get(0).status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(all.get(0).items()).hasSize(2);
        assertThat(service.searchLoans(f.prefix() + "EXTRA", 0, f.staff()).items())
                .extracting(r -> r.id()).containsExactly(firstLoan);
        assertThat(service.searchLoans(f.prefix() + "PM-2", 0, f.staff()).items())
                .extracting(r -> r.id()).containsExactly(secondLoan);
    }
}
