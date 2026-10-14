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
@SpringBootTest(properties = {"app.bootstrap-admin.password=", "app.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named = "S4_04_3_DB_TEST", matches = "true")
@WithMockUser(roles = "LIBRARY_MANAGER")
@Transactional
class CardLockSuggestionsDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LibraryCardService cards;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepository;
    @Autowired BookReservationService reservations;
    @Autowired FeePaymentService fees;
    @Autowired CardLockSuggestionService suggestions;
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


    Long pending(Fixture f) { return jdbc.queryForObject("SELECT id FROM card_lock_suggestions WHERE card_id=? AND status='PENDING'",Long.class,f.cardId()); }
    void threshold(Fixture f) { suggestions.updateSettings(new com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.UpdateSettings(java.math.BigDecimal.valueOf(100000)),f.manager()); }
    @Test void feeBoundaryIgnoreRecoveryAndReviolationUseOneEpisode() {
        var f=fixture();threshold(f);
        Long fee=jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id,amount_vnd) VALUES (?,100000) RETURNING id",Long.class,f.reader());
        suggestions.refresh();assertThat(jdbc.queryForObject("SELECT count(*) FROM card_lock_suggestions WHERE card_id=?",Long.class,f.cardId())).isZero();
        jdbc.update("UPDATE reader_fees SET amount_vnd=150000 WHERE id=?",fee);suggestions.refresh();Long id=pending(f);
        assertThat(suggestions.detail(id).currentUnpaidFeesVnd()).isEqualByComparingTo("150000");
        assertThat(suggestions.ignore(id,new com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Ignore("Theo dõi thêm"),f.manager()).suggestion().status()).isEqualTo("IGNORED");
        suggestions.refresh();assertThat(jdbc.queryForObject("SELECT count(*) FROM card_lock_suggestions WHERE card_id=?",Long.class,f.cardId())).isEqualTo(1L);
        var balance=fees.detail(f.reader(),f.staff());fees.collect(f.reader(),new CollectFullFeesRequest(UUID.randomUUID(),balance.totalOutstandingVnd(),balance.debtToken()),f.staff());
        suggestions.refresh();assertThat(suggestions.detail(id).suggestion().episodeOpen()).isFalse();
        jdbc.update("INSERT INTO reader_fees(reader_user_id,amount_vnd) VALUES (?,150000)",f.reader());suggestions.refresh();assertThat(pending(f)).isNotEqualTo(id);
    }
    @Test void acceptedProposalUsesManualLockGuardsButAllowsReturnAndFeeCollection() {
        var f=fixture();var borrowed=copy();var fresh=copy();var held=copy();threshold(f);
        var loan=loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(),f.cardNumber(),List.of(borrowed.barcode())),f.staff());
        Long item=jdbc.queryForObject("SELECT id FROM loan_items WHERE loan_id=?",Long.class,loan.loan().id());
        jdbc.update("INSERT INTO reader_fees(reader_user_id,amount_vnd) VALUES (?,150000)",f.reader());suggestions.refresh();Long id=pending(f);
        var decision=suggestions.accept(id,new LockLibraryCardRequest("Nợ phí vượt ngưỡng",OffsetDateTime.now().plusDays(7)),f.manager(),"127.0.0.1");
        assertThat(decision.accepted()).isTrue();assertThat(decision.card().status()).isEqualTo("LOCKED");
        assertThat(decision.suggestion().decidedBy()).isEqualTo(f.manager());assertThat(decision.suggestion().decidedAt()).isNotNull();
        assertThat(cards.getCardDetails(f.cardId()).history()).anyMatch(event->event.action().equals("LOCK"));
        assertThat(loans.readerEligibility(f.cardNumber(),f.staff()).blockReasons()).anyMatch(reason->reason.code().equals("LIBRARY_CARD_LOCKED"));
        assertThatThrownBy(()->reservations.reserve(held.book(),f.reader())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->loans.checkMyLoanRenewal(item,f.reader())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->loans.createDirectLoan(new CreateDirectLoanRequest(UUID.randomUUID(),f.cardNumber(),List.of(fresh.barcode())),f.staff())).isInstanceOf(ApiException.class);
        loans.confirmReturn(new ConfirmReturnRequest(borrowed.barcode(),item),f.staff());
        var balance=fees.detail(f.reader(),f.staff());fees.collect(f.reader(),new CollectFullFeesRequest(UUID.randomUUID(),balance.totalOutstandingVnd(),balance.debtToken()),f.staff());
        assertThat(cards.getMyCard(f.reader()).cardStatus()).isEqualTo("LOCKED");
        assertThat(fees.detail(f.reader(),f.staff()).totalOutstandingVnd()).isZero();
    }
    @Test void settledDebtAndManualLockBeforeDecisionNeverCreateSecondLock() {
        var f=fixture();threshold(f);jdbc.update("INSERT INTO reader_fees(reader_user_id,amount_vnd) VALUES (?,150000)",f.reader());suggestions.refresh();Long id=pending(f);
        var balance=fees.detail(f.reader(),f.staff());fees.collect(f.reader(),new CollectFullFeesRequest(UUID.randomUUID(),balance.totalOutstandingVnd(),balance.debtToken()),f.staff());
        var result=suggestions.accept(id,new LockLibraryCardRequest("Nợ phí",null),f.manager(),"127.0.0.1");assertThat(result.accepted()).isFalse();assertThat(result.suggestion().status()).isEqualTo("RESOLVED");
        jdbc.update("INSERT INTO reader_fees(reader_user_id,amount_vnd) VALUES (?,150000)",f.reader());suggestions.refresh();Long next=pending(f);
        cards.lockCard(f.cardId(),new LockLibraryCardRequest("Khóa thủ công",null),f.manager(),"127.0.0.1");
        var stale=suggestions.accept(next,new LockLibraryCardRequest("Đề xuất",null),f.manager(),"127.0.0.1");assertThat(stale.accepted()).isFalse();assertThat(stale.suggestion().status()).isEqualTo("CANCELLED_LOCKED");
        assertThat(cards.getCardDetails(f.cardId()).history()).hasSize(1);
    }

    @Autowired LibraryConfigurationService configuration;
    Long historicalLoan(Fixture f, Copy copy, int days) {
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var borrowed=today.minusDays(50).atTime(8,0).atOffset(java.time.ZoneOffset.ofHours(7));
        var due=today.minusDays(days).atTime(17,0).atOffset(java.time.ZoneOffset.ofHours(7));
        Long loan=jdbc.queryForObject("INSERT INTO loans(loan_number,borrower_user_id,created_by,borrowed_at) VALUES (?,?,?,?) RETURNING id",Long.class,"S4043-"+UUID.randomUUID(),f.reader(),f.staff(),borrowed);
        return jdbc.queryForObject("INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date) VALUES (?,(SELECT id FROM book_copies WHERE barcode=?),?,?) RETURNING id",Long.class,loan,copy.barcode(),borrowed,due);
    }
    @Test void exactThirtyThirtyOneMultipleLoansAndReturnBeforeApproval() {
        var f=fixture();var firstCopy=copy();var secondCopy=copy();
        jdbc.update("UPDATE library_weekly_schedule SET is_open=TRUE,open_time='08:00',close_time='17:00'");
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("DELETE FROM library_closed_dates WHERE closed_date BETWEEN ? AND ?",today.minusDays(50),today);
        threshold(f);Long item=historicalLoan(f,firstCopy,30);suggestions.refresh();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card_lock_suggestions WHERE card_id=?",Long.class,f.cardId())).isZero();
        jdbc.update("UPDATE loan_items SET due_date=? WHERE id=?",today.minusDays(31).atTime(17,0).atOffset(java.time.ZoneOffset.ofHours(7)),item);suggestions.refresh();Long id=pending(f);
        assertThat(suggestions.detail(id).currentMaxOverdueDays()).isEqualTo(31);
        Long second=historicalLoan(f,secondCopy,35);suggestions.refresh();var details=suggestions.detail(id);
        assertThat(details.currentMaxOverdueDays()).isEqualTo(35);assertThat(details.currentEvidence().overdueItems()).hasSize(2);
        configuration.updateOverdueFeeSetting(new com.duanttcsn5.library.dto.libraryconfig.OverdueFeeSettingRequest(java.math.BigDecimal.ZERO),f.manager(),"127.0.0.1");
        loans.confirmReturn(new ConfirmReturnRequest(firstCopy.barcode(),item),f.staff());loans.confirmReturn(new ConfirmReturnRequest(secondCopy.barcode(),second),f.staff());
        var decision=suggestions.accept(id,new LockLibraryCardRequest("Quá hạn",null),f.manager(),"127.0.0.1");
        assertThat(decision.accepted()).isFalse();assertThat(decision.suggestion().status()).isEqualTo("RESOLVED");assertThat(cards.getMyCard(f.reader()).cardStatus()).isEqualTo("ACTIVE");
    }
}
