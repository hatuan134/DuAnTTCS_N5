package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookReservationRepository;
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

/** Opt-in PostgreSQL test; use a disposable database. Fixtures roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S3_05_2_DB_TEST", matches = "true")
@Transactional
class ReaderRenewalWaitingReadersDatabaseTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookReservationRepository reservations;
    @Autowired private LoanRepository loans;
    @Autowired private LoanService service;

    private static final OffsetDateTime CHECKED_AT = OffsetDateTime.parse("2026-10-08T09:00:00Z");
    private record Fixture(Long bookId, Long readerId, Long otherId, Long itemId,
                           OffsetDateTime dueAt) {}

    private Long reader() {
        String unique = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = 'READER' RETURNING id
                """, Long.class, "S3052 " + unique, unique + "@example.invalid");
    }

    private Fixture fixture() {
        Long owner = reader();
        Long other = reader();
        Long book = jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S3052 " + UUID.randomUUID());
        Long copy = jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 1, 50000, 'GOOD')
                RETURNING id
                """, Long.class, book, "S3052-" + UUID.randomUUID());
        Long loan = loans.insert(null, owner, owner, "PM-S3052-" + UUID.randomUUID(), CHECKED_AT);
        OffsetDateTime due = CHECKED_AT.plusDays(15);
        loans.insertItem(loan, copy, CHECKED_AT, due);
        Long item = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id = ?", Long.class, loan);
        return new Fixture(book, owner, other, item, due);
    }

    private void reserve(Long bookId, Long readerId, String status, OffsetDateTime deadline) {
        jdbc.update("""
                INSERT INTO book_reservations(book_id, reader_id, status, reserved_at, pickup_deadline)
                VALUES (?, ?, ?, ?, ?)
                """, bookId, readerId, status, CHECKED_AT.minusDays(2), deadline);
    }

    private boolean blocked(Fixture f) {
        return reservations.existsOtherEffectiveReservationForLoanItem(f.itemId(), f.readerId(), CHECKED_AT);
    }

    private void unchanged(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT due_date FROM loan_items WHERE id = ?",
                OffsetDateTime.class, f.itemId()).toInstant()).isEqualTo(f.dueAt().toInstant());
    }

    @Test void noWaitersAndOwnReservationDoNotBlock() {
        Fixture f = fixture();
        assertThat(blocked(f)).isFalse();
        reserve(f.bookId(), f.readerId(), "PENDING", null);
        assertThat(blocked(f)).isFalse();
        unchanged(f);
    }

    @Test void oneOtherOrManyOtherReadersBlockByBookTitle() {
        Fixture f = fixture();
        reserve(f.bookId(), f.otherId(), "PENDING", null);
        assertThat(blocked(f)).isTrue();
        reserve(f.bookId(), reader(), "PENDING", null);
        assertThat(blocked(f)).isTrue();
        unchanged(f);
    }

    @Test void inactiveStatusesAndExpiredPickupDeadlinesDoNotBlock() {
        Fixture f = fixture();
        for (String status : new String[]{"CANCELLED", "FULFILLED", "EXPIRED"}) {
            reserve(f.bookId(), f.otherId(), status, null);
        }
        reserve(f.bookId(), f.otherId(), "READY_FOR_PICKUP", CHECKED_AT.minusSeconds(1));
        assertThat(blocked(f)).isFalse();
        reserve(f.bookId(), reader(), "READY_FOR_PICKUP", CHECKED_AT);
        assertThat(blocked(f)).isTrue(); // Equality is still within the pickup window.
        unchanged(f);
    }

    @Test void unrelatedTitleDoesNotBlockAndRejectionDoesNotUpdateDueDate() {
        Fixture f = fixture();
        Fixture unrelated = fixture();
        reserve(unrelated.bookId(), f.otherId(), "PENDING", null);
        assertThat(blocked(f)).isFalse();
        reserve(f.bookId(), f.otherId(), "PENDING", null);
        assertThat(blocked(f)).isTrue();
        // Real service uses its own live Clock, so only assert rejection if fixture's
        // due date is still in the future relative to execution time.
        if (f.dueAt().toInstant().isAfter(java.time.Instant.now())) {
            assertThatThrownBy(() -> service.checkMyLoanRenewal(f.itemId(), f.readerId()))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_RESERVATION"));
        }
        unchanged(f);
    }
}
