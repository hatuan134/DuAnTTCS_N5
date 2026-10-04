package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Opt in with S2_07_4_DB_TEST=true on a dedicated PostgreSQL 15 database.
 * Exercises the real loan/return trigger and reservation queries/service.
 * Fixtures roll back, including protected loan items; migrations remain and
 * sequences may advance. Requires the project's seeded master data/calendar.
 */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S2_07_4_DB_TEST", matches = "true")
@Transactional
class BookReservationBorrowedTitleDatabaseTest {
    @Autowired private BookReservationService service;
    @Autowired private BookReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(Long readerId, Long staffId, List<Long> books) {}
    private record Loan(Long id, List<Long> items) {}

    private Long user(String role) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO users(role_id, full_name, email, status)
                SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code = ? RETURNING id
                """, Long.class, "S2074 " + role + " " + suffix, suffix + "@example.invalid", role);
    }

    private Fixture fixture() {
        Long reader = user("READER"), staff = user("LIBRARIAN");
        jdbc.update("""
                INSERT INTO library_cards(card_number, user_id, card_type_id, expires_at, status)
                VALUES (?, ?, (SELECT MIN(id) FROM card_types), CURRENT_DATE + 30, 'ACTIVE')
                """, "S2074-" + UUID.randomUUID(), reader);
        List<Long> books = new ArrayList<>();
        for (int i = 0; i < 3; i++) books.add(jdbc.queryForObject("""
                INSERT INTO books(title, author_id, category_id)
                VALUES (?, (SELECT MIN(id) FROM authors), (SELECT MIN(id) FROM categories)) RETURNING id
                """, Long.class, "S2074 sách " + i + " " + UUID.randomUUID()));
        return new Fixture(reader, staff, List.copyOf(books));
    }

    private Long copy(Long bookId) {
        return jdbc.queryForObject("""
                INSERT INTO book_copies(book_id, barcode, shelf_id, status,
                    received_date, cover_price, physical_condition)
                VALUES (?, ?, (SELECT MIN(id) FROM shelves), 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD')
                RETURNING id
                """, Long.class, bookId, "S2074-" + UUID.randomUUID());
    }

    private Loan borrow(Fixture fixture, Long borrower, Long... copies) {
        OffsetDateTime time = OffsetDateTime.now().minusDays(7);
        Long loanId = jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, "S2074-PM-" + UUID.randomUUID(), borrower, fixture.staffId(), time);
        List<Long> items = new ArrayList<>();
        for (Long copy : copies) items.add(jdbc.queryForObject("""
                INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, loanId, copy, time, OffsetDateTime.now().minusDays(1)));
        return new Loan(loanId, List.copyOf(items));
    }

    private void returned(Long itemId) {
        // V13 performs the AVAILABLE transition; never manually overwrite copy status.
        assertThat(jdbc.update("UPDATE loan_items SET returned_at = clock_timestamp() WHERE id = ? AND returned_at IS NULL",
                itemId)).isEqualTo(1);
    }

    private long pending(Fixture fixture, Long bookId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status) VALUES (?, ?, ?) RETURNING id
                """, Long.class, bookId, fixture.readerId(), status);
    }

    private void assertBorrowedRejected(Fixture fixture, Long bookId) {
        String title = jdbc.queryForObject("SELECT title FROM books WHERE id = ?", String.class, bookId);
        assertThatThrownBy(() -> service.reserve(bookId, fixture.readerId()))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("BOOK_ALREADY_BORROWED");
                    assertThat(error.getMessage()).contains(title, "chưa trả");
                });
    }

    @Test void noOwnUnreturnedLoanAllowsNormalPendingReservation() {
        Fixture f = fixture();
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(f.readerId(), f.books().get(0))).isFalse();
        assertThat(service.reserve(f.books().get(0), f.readerId()).status()).isEqualTo("PENDING");
    }

    @Test void ownUnreturnedOverdueCopyRejectsEvenWhenAnotherCopyIsAvailableAndPreservesQueue() {
        Fixture f = fixture(); Long title = f.books().get(0), borrowed = copy(title), available = copy(title);
        borrow(f, f.readerId(), borrowed);
        Long waitingReader = user("READER");
        Long waiting = jdbc.queryForObject("""
                INSERT INTO book_reservations(book_id, reader_id, status) VALUES (?, ?, 'PENDING') RETURNING id
                """, Long.class, title, waitingReader);
        List<java.util.Map<String, Object>> beforeQueue = jdbc.queryForList(
                "SELECT * FROM book_reservations WHERE book_id = ? ORDER BY id", title);
        List<java.util.Map<String, Object>> beforeCopies = jdbc.queryForList(
                "SELECT * FROM book_copies WHERE book_id = ? ORDER BY id", title);
        assertBorrowedRejected(f, title);
        assertThat(jdbc.queryForList("SELECT * FROM book_reservations WHERE book_id = ? ORDER BY id", title))
                .isEqualTo(beforeQueue);
        assertThat(jdbc.queryForList("SELECT * FROM book_copies WHERE book_id = ? ORDER BY id", title))
                .isEqualTo(beforeCopies);
        assertThat(reservations.findPendingQueuePosition(waiting)).isEqualTo(1L);
        assertThat(copyStatus(borrowed)).isEqualTo("BORROWED");
        assertThat(copyStatus(available)).isEqualTo("AVAILABLE");
    }

    @Test void multipleUnreturnedCopiesOfDifferentTitlesDoNotBlockRequestedTitle() {
        Fixture f = fixture();
        borrow(f, f.readerId(), copy(f.books().get(1)), copy(f.books().get(2)));
        assertThat(service.reserve(f.books().get(0), f.readerId()).status()).isEqualTo("PENDING");
    }

    @Test void otherReadersUnreturnedCopyDoesNotBlockThisReader() {
        Fixture f = fixture(); Long title = f.books().get(0), other = user("READER");
        borrow(f, other, copy(title));
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(other, title)).isTrue();
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(f.readerId(), title)).isFalse();
        assertThat(service.reserve(title, f.readerId()).status()).isEqualTo("PENDING");
    }

    @Test void returningOnlyOneOfTwoCopiesStillBlocksUntilLastCopyIsReturned() {
        Fixture f = fixture(); Long title = f.books().get(0), first = copy(title), second = copy(title);
        Loan loan = borrow(f, f.readerId(), first, second);
        returned(loan.items().get(0));
        assertThat(copyStatus(first)).isEqualTo("AVAILABLE");
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(f.readerId(), title)).isTrue();
        assertBorrowedRejected(f, title);
        returned(loan.items().get(1));
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(f.readerId(), title)).isFalse();
        var response = service.reserve(title, f.readerId());
        assertThat(response.status()).isEqualTo("READY_FOR_PICKUP");
        assertThat(response.reservedCopy().copyId()).isEqualTo(first);
        assertThat(copyStatus(first)).isEqualTo("HELD");
        assertThat(copyStatus(second)).isEqualTo("AVAILABLE");
    }

    @Test void returnedTargetIsAllowedEvenWhenSameLoanHasAnotherTitleOutstanding() {
        Fixture f = fixture(); Long title = f.books().get(0), targetCopy = copy(title);
        Loan loan = borrow(f, f.readerId(), targetCopy, copy(f.books().get(1)));
        returned(loan.items().get(0));
        assertThat(reservations.hasUnreturnedLoanForReaderAndBook(f.readerId(), f.books().get(1))).isTrue();
        assertThat(service.reserve(title, f.readerId()).status()).isEqualTo("READY_FOR_PICKUP");
    }

    @Test void returnedTargetStillHasToPassExistingDuplicateRule() {
        Fixture f = fixture(); Long title = f.books().get(0);
        Loan loan = borrow(f, f.readerId(), copy(title)); returned(loan.items().get(0));
        pending(f, title, "PENDING");
        assertThatThrownBy(() -> service.reserve(title, f.readerId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_ALREADY_ACTIVE"));
    }

    @Test void returnedTargetStillHasToPassExistingThreeActiveLimit() {
        Fixture f = fixture(); Long title = f.books().get(0);
        Loan loan = borrow(f, f.readerId(), copy(title)); returned(loan.items().get(0));
        // A legacy fixture may contain multiple active rows of another title;
        // existing S2-07.3 must still count every active row, not distinct titles.
        for (int i = 0; i < 3; i++) pending(f, f.books().get(1), "PENDING");
        assertThatThrownBy(() -> service.reserve(title, f.readerId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_LIMIT_REACHED"));
        assertThat(reservations.countActiveForReader(f.readerId())).isEqualTo(3L);
    }

    @Test void fulfilledReservationHistoryDoesNotHideAnUnreturnedLoan() {
        Fixture f = fixture(); Long title = f.books().get(0);
        pending(f, title, "FULFILLED"); borrow(f, f.readerId(), copy(title));
        assertBorrowedRejected(f, title);
    }

    @Test void loanItemIsAuthoritativeEvenWhenLegacyCopyStatusIsInconsistent() {
        Fixture f = fixture(); Long title = f.books().get(0), copyId = copy(title);
        borrow(f, f.readerId(), copyId);
        // Simulate legacy inconsistent data on this rollback-only test fixture.
        jdbc.update("UPDATE book_copies SET status = 'AVAILABLE' WHERE id = ?", copyId);
        assertBorrowedRejected(f, title);
        assertThat(copyStatus(copyId)).isEqualTo("AVAILABLE");
    }

    private String copyStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM book_copies WHERE id = ?", String.class, id);
    }
}
