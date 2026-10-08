package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectLoanEligibilityServiceTest {
    private LibraryCardRepository cards;
    private LoanRepository loans;
    private UserRepository users;
    private LoanService service;
    private LibraryCard card;
    private User reader;
    private CardType type;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @BeforeEach void setup() {
        cards = mock(LibraryCardRepository.class); loans = mock(LoanRepository.class);
        users = mock(UserRepository.class);
        // UTC date is still October 7; the library's calendar date is October 8.
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC);
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), cards, users, loans, mock(LibraryConfigurationService.class), clock);
        User staff = user(3L, "LIBRARIAN");
        when(users.findById(3L)).thenReturn(Optional.of(staff));
        reader = user(12L, "READER"); reader.setFullName("Nguyễn Văn An");
        type = new CardType(); type.setName("Thẻ sinh viên"); type.setMaxBooks(5);
        card = new LibraryCard(); card.setCardNumber("TV-0012"); card.setUser(reader); card.setCardType(type);
        card.setIssuedAt(TODAY.minusDays(10)); card.setExpiresAt(TODAY.plusDays(30));
        when(cards.findByCardNumberWithDetails("TV-0012")).thenReturn(Optional.of(card));
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(2L);
    }

    private User user(Long id, String code) {
        User u = new User(); u.setId(id); u.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(code); u.setRole(role); return u;
    }

    @Test void trimsCodeAndReturnsExactReaderTypeOutstandingCountAndQuota() {
        var result = service.readerEligibility("  TV-0012  ", 3L);
        assertThat(result.readerId()).isEqualTo(12L);
        assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.cardTypeName()).isEqualTo("Thẻ sinh viên");
        assertThat(result.cardNumber()).isEqualTo("TV-0012");
        assertThat(result.maxBooks()).isEqualTo(5);
        assertThat(result.borrowedBooks()).isEqualTo(2L);
        assertThat(result.remainingBooks()).isEqualTo(3L);
        assertThat(result.message()).contains("đang mượn 2/5");
        assertThat(result.eligible()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("ELIGIBLE");
        verify(loans).countUnreturnedBooksForReader(12L);
    }

    @Test void zeroLoansAllowsFullQuota() {
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(0L);
        assertThat(service.readerEligibility("TV-0012", 3L).remainingBooks()).isEqualTo(5);
    }

    @Test void atLimitOverLimitAndZeroPolicyNeverProduceNegativeRemaining() {
        for (long count : new long[]{5, 6, 20}) {
            when(loans.countUnreturnedBooksForReader(12L)).thenReturn(count);
            blocked("LOAN_LIMIT_REACHED");
            assertThat(service.readerEligibility("TV-0012", 3L).message()).contains("đang mượn " + count + "/5");
            assertThat(service.readerEligibility("TV-0012", 3L).borrowedBooks()).isEqualTo(count);
        }
        type.setMaxBooks(0); when(loans.countUnreturnedBooksForReader(12L)).thenReturn(0L);
        blocked("LOAN_LIMIT_REACHED");
    }

    @Test void missingAndInvalidCodesUseExistingDomainErrors() {
        for (String number : new String[]{null, "", "   ", "x".repeat(101)}) {
            assertThatThrownBy(() -> service.readerEligibility(number, 3L))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("INVALID_CARD_NUMBER");
                    });
        }
        assertThatThrownBy(() -> service.readerEligibility("UNKNOWN", 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(404);
                    assertThat(e.getCode()).isEqualTo("LIBRARY_CARD_NOT_FOUND");
                });
        verifyNoInteractions(loans);
    }

    @Test void expiryDateIsInclusiveAndUsesVietnamCalendar() {
        card.setExpiresAt(TODAY);
        assertThat(service.readerEligibility("TV-0012", 3L).eligible()).isTrue();
        card.setExpiresAt(TODAY.minusDays(1)); blocked("LIBRARY_CARD_EXPIRED");
        card.setExpiresAt(TODAY.plusDays(30)); card.setStatus("EXPIRED"); blocked("LIBRARY_CARD_EXPIRED");
    }

    @Test void lockedInactiveAndFutureCardsShowReaderButBlockBorrowing() {
        card.setStatus("LOCKED"); blocked("LIBRARY_CARD_LOCKED");
        card.setStatus("INACTIVE"); blocked("LIBRARY_CARD_INACTIVE");
        card.setStatus("ACTIVE"); card.setExpiresAt(null); blocked("LIBRARY_CARD_INACTIVE");
        card.setExpiresAt(TODAY.plusDays(30)); card.setIssuedAt(TODAY.plusDays(1));
        blocked("LIBRARY_CARD_NOT_YET_VALID");
        card.setIssuedAt(null); blocked("LIBRARY_CARD_NOT_YET_VALID");
    }

    @Test void readerWithoutBorrowingRightsAndInvalidPolicyAreBlocked() {
        reader.setStatus("LOCKED"); blocked("READER_ACCOUNT_INACTIVE");
        reader.setStatus("ACTIVE"); reader.getRole().setCode("LIBRARIAN"); blocked("READER_ROLE_REQUIRED");
        reader.getRole().setCode("READER"); type.setActive(false); blocked("CARD_TYPE_INACTIVE");
        type.setActive(true); type.setMaxBooks(-1); blocked("LOAN_POLICY_NOT_CONFIGURED");
        type.setMaxBooks(11); blocked("LOAN_POLICY_NOT_CONFIGURED");
    }

    @Test void serviceAlsoRejectsUnauthenticatedReaderAndInactiveStaff() {
        assertThatThrownBy(() -> service.readerEligibility("TV-0012", null)).isInstanceOf(ApiException.class);
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "READER")));
        assertThatThrownBy(() -> service.readerEligibility("TV-0012", 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        User inactive = user(3L, "LIBRARIAN"); inactive.setStatus("LOCKED");
        when(users.findById(3L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.readerEligibility("TV-0012", 3L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(cards, loans);
    }

    private void blocked(String reason) {
        var result = service.readerEligibility("TV-0012", 3L);
        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.remainingBooks()).isZero();
        assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.cardTypeName()).isEqualTo("Thẻ sinh viên");
    }
}
