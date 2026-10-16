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
@EnabledIfEnvironmentVariable(named = "S4_04_2_DB_TEST", matches = "true")
@WithMockUser(roles = "LIBRARY_MANAGER")
@Transactional
class ManualLibraryCardUnlockDatabaseTest {
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
                Long.class, "S4042 " + role, UUID.randomUUID() + "@example.invalid", role);
    }
    Fixture fixture() {
        Long reader = user("READER"), manager = user("LIBRARY_MANAGER"), staff = user("LIBRARIAN");
        jdbc.update("INSERT INTO reader_profiles(user_id, member_code, date_of_birth, registration_status) VALUES (?, ?, '2006-09-22', 'APPROVED')", reader, "S4042-" + UUID.randomUUID());
        Long type = jdbc.queryForObject("INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days) VALUES (?, 5, 14, 3, 7) RETURNING id", Long.class, "S4042-" + UUID.randomUUID());
        String number = "S4042-" + UUID.randomUUID();
        Long card = jdbc.queryForObject("INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE-1, CURRENT_DATE+365, 'ACTIVE') RETURNING id", Long.class, number, reader, type);
        return new Fixture(reader, manager, staff, card, number);
    }
    Copy copy() {
        String unique = "S4042-" + UUID.randomUUID();
        Long author = jdbc.queryForObject("INSERT INTO authors(name) VALUES (?) RETURNING id", Long.class, unique);
        Long category = jdbc.queryForObject("INSERT INTO categories(name) VALUES (?) RETURNING id", Long.class, unique);
        Long warehouse = jdbc.queryForObject("INSERT INTO warehouses(code, name) VALUES (?, ?) RETURNING id", Long.class, unique, unique);
        Long shelf = jdbc.queryForObject("INSERT INTO shelves(warehouse_id, code, name) VALUES (?, 'A1', 'Kệ kiểm thử') RETURNING id", Long.class, warehouse);
        Long book = jdbc.queryForObject("INSERT INTO books(title, author_id, category_id) VALUES (?, ?, ?) RETURNING id", Long.class, unique, author, category);
        String barcode = "S4042-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition) VALUES (?, ?, ?, 'AVAILABLE', CURRENT_DATE-1, 30000, 'GOOD')", book, barcode, shelf);
        return new Copy(book, barcode);
    }

    void cycle(Fixture f, OffsetDateTime end) {
        var locked = cards.lockCard(f.cardId(), new LockLibraryCardRequest("Vi phạm quy định thư viện", end), f.manager(), "127.0.0.1");
        cards.unlockCard(f.cardId(), new com.duanttcsn5.library.dto.librarycard.UnlockLibraryCardRequest(
            "Bạn đọc đã hoàn thành xử lý vi phạm", locked.manualLock().startedAt()), f.manager(), "127.0.0.1");
    }
    @Test void timedAndIndefiniteReleaseKeepAllEventsAndReenableTransactionsWhenEligible() {
        var f = fixture(); var borrowed = copy(); var held = copy(); var fresh = copy();
        var loan = loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(borrowed.barcode())), f.staff());
        Long itemId = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?", Long.class, loan.loan().id());
        cycle(f, OffsetDateTime.now().plusDays(7)); cycle(f, null);
        assertThat(cards.getMyCard(f.reader()).cardStatus()).isEqualTo("ACTIVE");
        assertThat(loans.readerEligibility(f.cardNumber(), f.staff()).eligible()).isTrue();
        assertThat(loans.checkMyLoanRenewal(itemId, f.reader()).eligible()).isTrue();
        assertThat(reservations.reserve(held.book(), f.reader())).isNotNull();
        assertThat(loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(fresh.barcode())), f.staff())).isNotNull();
        var details = cards.getCardDetails(f.cardId()); assertThat(details.history()).hasSize(4);
        var unlock = details.history().get(0);
        assertThat(unlock.action()).isEqualTo("UNLOCK"); assertThat(unlock.actorUserId()).isEqualTo(f.manager());
        assertThat(unlock.lockActorUserId()).isEqualTo(f.manager()); assertThat(unlock.actorName()).contains("LIBRARY_MANAGER");
        assertThat(unlock.reason()).isEqualTo("Bạn đọc đã hoàn thành xử lý vi phạm");
        assertThat(unlock.lockReason()).isEqualTo("Vi phạm quy định thư viện");
        assertThat(unlock.performedAt()).isAfterOrEqualTo(unlock.lockStartedAt());
        assertThat(unlock.previousStatus()).isEqualTo("LOCKED"); assertThat(unlock.resultingStatus()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='LIBRARY_CARD_MANUALLY_UNLOCKED' AND entity_id=?", Long.class, f.cardId().toString())).isEqualTo(2);
    }
    @Test void debtRemainsAndLoanAndRenewalChecksStillRejectAfterManualRelease() {
        var f = fixture(); var borrowed = copy();
        var loan = loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(), f.cardNumber(), List.of(borrowed.barcode())), f.staff());
        Long itemId = jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?", Long.class, loan.loan().id());
        jdbc.update("INSERT INTO reader_fees(reader_user_id, amount_vnd) VALUES (?, 15000)", f.reader());
        cycle(f, null);
        assertThat(cards.getMyCard(f.reader()).cardStatus()).isEqualTo("ACTIVE");
        assertThat(loanRepository.sumUnpaidFeesForReader(f.reader())).isEqualByComparingTo("15000");
        assertThat(loans.readerEligibility(f.cardNumber(), f.staff()).eligible()).isFalse();
        assertThatThrownBy(() -> loans.checkMyLoanRenewal(itemId, f.reader())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_VIOLATIONS"));
    }
}
