package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Run on disposable PostgreSQL only. Fixtures and card audit changes roll back; existing REQUIRES_NEW rejection logs may remain. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_04_1_DB_TEST", matches = "true")
@WithMockUser(roles = "LIBRARY_MANAGER")
@Transactional
class ManualLibraryCardLockDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LibraryCardService cards;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepository;
    @Autowired BookReservationService reservations;
    @Autowired FeePaymentService fees;
    record Fixture(Long reader, Long manager, Long staff, Long cardId, String cardNumber) {}
    record Copy(Long book, String barcode) {}
    Long user(String role) {
        return jdbc.queryForObject("INSERT INTO users(role_id, full_name, email, status) SELECT id, ?, ?, 'ACTIVE' FROM roles WHERE code=? RETURNING id",
                Long.class, "S4041 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture() {
        Long reader = user("READER"), manager = user("LIBRARY_MANAGER"), staff = user("LIBRARIAN");
        jdbc.update("INSERT INTO reader_profiles(user_id, member_code, date_of_birth, registration_status) VALUES (?, ?, '2006-09-22', 'APPROVED')", reader, "S4041-" + UUID.randomUUID());
        Long type = jdbc.queryForObject("INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days) VALUES (?, 5, 14, 3, 7) RETURNING id", Long.class, "S4041-" + UUID.randomUUID());
        String number = "S4041-" + UUID.randomUUID();
        Long card = jdbc.queryForObject("INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE-1, CURRENT_DATE+365, 'ACTIVE') RETURNING id", Long.class, number, reader, type);
        return new Fixture(reader, manager, staff, card, number);
    }
    Copy copy() {
        String unique = "S4041-" + UUID.randomUUID();
        Long author = jdbc.queryForObject("INSERT INTO authors(name) VALUES (?) RETURNING id", Long.class, unique);
        Long category = jdbc.queryForObject("INSERT INTO categories(name) VALUES (?) RETURNING id", Long.class, unique);
        Long warehouse = jdbc.queryForObject("INSERT INTO warehouses(code, name) VALUES (?, ?) RETURNING id", Long.class, unique, unique);
        Long shelf = jdbc.queryForObject("INSERT INTO shelves(warehouse_id, code, name) VALUES (?, 'A1', 'Kệ kiểm thử') RETURNING id", Long.class, warehouse);
        Long book = jdbc.queryForObject("INSERT INTO books(title, author_id, category_id) VALUES (?, ?, ?) RETURNING id", Long.class, unique, author, category);
        String barcode = "S4041-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition) VALUES (?, ?, ?, 'AVAILABLE', CURRENT_DATE-1, 30000, 'GOOD')", book, barcode, shelf);
        return new Copy(book, barcode);
    }
    void rejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("LIBRARY_CARD_LOCKED");
            assertThat(e.getMessage()).contains("Vi phạm quy định thư viện");
        });
    }
    void lock(Fixture f) {
        cards.lockCard(f.cardId(), new LockLibraryCardRequest("Vi phạm quy định thư viện", null), f.manager(), "127.0.0.1");
    }
    @Test void existingLoanAndReservationSurviveButNewLoanReservationAndRenewalAreRejected() {
        var f = fixture(); var borrowed = copy(); var held = copy(); var fresh = copy();
        var loan = loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(borrowed.barcode())), f.staff());
        Long itemId = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?", Long.class, loan.loan().id());
        var reservation = reservations.reserve(held.book(), f.reader());
        lock(f);
        assertThat(jdbc.queryForObject("SELECT returned_at IS NULL FROM loan_items WHERE id=?", Boolean.class, itemId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM book_reservations WHERE id=?", String.class, reservation.id())).isEqualTo(reservation.status());
        rejected(() -> loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(fresh.barcode())), f.staff()));
        rejected(() -> reservations.reserve(fresh.book(), f.reader()));
        rejected(() -> reservations.reserveMany(fresh.book(), f.reader(), 1));
        rejected(() -> loans.checkMyLoanRenewal(itemId, f.reader()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE borrower_user_id=?", Long.class, f.reader())).isEqualTo(1);
        assertThat(cards.getMyCard(f.reader()).manualLock().reason()).isEqualTo("Vi phạm quy định thư viện");
    }
    @Test void returnsAndFullPaymentDoNotRemoveManualLockAndAuditContainsActorAndDuration() {
        var f = fixture(); var borrowed = copy();
        var loan = loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(borrowed.barcode())), f.staff());
        Long itemId = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?", Long.class, loan.loan().id());
        lock(f);
        loans.confirmReturn(new ConfirmReturnRequest(borrowed.barcode(), itemId), f.staff());
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd) VALUES (?, 15000)", f.reader());
        var balance = fees.detail(f.reader(), f.staff());
        fees.collect(f.reader(), new CollectFullFeesRequest(UUID.randomUUID(), balance.totalOutstandingVnd(), balance.debtToken()), f.staff());
        assertThat(loanRepository.sumUnpaidFeesForReader(f.reader())).isZero();
        assertThat(cards.getMyCard(f.reader()).manualLock().active()).isTrue();
        assertThat(cards.getMyCard(f.reader()).cardStatus()).isEqualTo("LOCKED");
        var log = jdbc.queryForMap("SELECT actor_user_id, after_data::text AS data FROM audit_logs WHERE action='LIBRARY_CARD_MANUALLY_LOCKED' AND entity_id=?", f.cardId().toString());
        assertThat(((Number)log.get("actor_user_id")).longValue()).isEqualTo(f.manager());
        assertThat(log.get("data").toString()).contains("Vi phạm quy định thư viện", "startedAt", "endsAt", "indefinite");
    }
    @Test void expiredLockStopsBlockingWithoutAScheduledJobAndIndefiniteLockRemains() {
        var f = fixture();
        cards.lockCard(f.cardId(), new LockLibraryCardRequest("Vi phạm quy định thư viện", OffsetDateTime.now().plusDays(7)), f.manager(), "127.0.0.1");
        jdbc.update("UPDATE library_cards SET manual_lock_started_at=CURRENT_TIMESTAMP-INTERVAL '2 days', manual_lock_ends_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE id=?", f.cardId());
        // Native update can leave managed state cached inside this test transaction; clear it.
        entityManager.clear();
        assertThat(cards.getMyCard(f.reader()).manualLock().active()).isFalse();
        assertThat(loans.readerEligibility(f.cardNumber(), f.staff()).eligible()).isTrue();
        lock(f);
        assertThat(cards.getMyCard(f.reader()).manualLock().endsAt()).isNull();
        assertThat(cards.getMyCard(f.reader()).manualLock().active()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='LIBRARY_CARD_MANUALLY_LOCKED' AND entity_id=?", Long.class, f.cardId().toString())).isEqualTo(2);
    }
    @Autowired jakarta.persistence.EntityManager entityManager;
}
