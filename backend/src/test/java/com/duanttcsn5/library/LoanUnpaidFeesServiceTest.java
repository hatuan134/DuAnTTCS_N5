package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.CreateDirectLoanRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanUnpaidFeesServiceTest {
    private BookRepository books;
    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private LibraryCardRepository cards;
    private UserRepository users;
    private LoanRepository loans;
    private LoanService service;
    private LibraryCard card;
    private Book book;
    private BookCopy copy;
    private BookReservation reservation;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T10:00:00+07:00");

    private static User user(Long id, String roleCode) {
        User user = new User();
        user.setId(id); user.setStatus("ACTIVE"); user.setFullName("Bạn đọc thử nghiệm");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        return user;
    }

    @BeforeEach
    void setup() {
        books = mock(BookRepository.class);
        reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class);
        cards = mock(LibraryCardRepository.class);
        users = mock(UserRepository.class);
        loans = mock(LoanRepository.class);
        service = new LoanService(books, reservations, copies, cards, users, loans,
                mock(LibraryConfigurationService.class),
                Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "LIBRARIAN")));
        User reader = user(12L, "READER");
        CardType type = new CardType();
        type.setName("Thẻ sinh viên"); type.setMaxBooks(5); type.setLoanDays(14);
        card = new LibraryCard();
        card.setUser(reader); card.setCardNumber("TV-0012"); card.setCardType(type);
        card.setIssuedAt(NOW.toLocalDate().minusDays(1));
        card.setExpiresAt(NOW.toLocalDate().plusDays(30));
        when(cards.findByCardNumberWithDetails("TV-0012")).thenReturn(Optional.of(card));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        when(loans.findReaderIdByCardNumber("TV-0012")).thenReturn(Optional.of(12L));
        when(loans.lockDirectLoanCard("TV-0012")).thenReturn(Optional.of(12L));
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(BigDecimal.ZERO);
        book = new Book(); book.setId(7L); book.setTitle("Sách thử nghiệm");
        copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(31L);
        when(copy.getBook()).thenReturn(book);
        when(copy.getBarcode()).thenReturn("BC-031");
        when(copy.getStatus()).thenReturn("AVAILABLE");
        when(loans.findCopyIdentity("BC-031")).thenReturn(
                Optional.of(new LoanRepository.CopyIdentity(31L, 7L)));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(copies.findForStatusChange(31L)).thenReturn(Optional.of(copy));
        reservation = new BookReservation(book, reader, "READY_FOR_PICKUP");
        reservation.setId(21L); reservation.setBookCopy(copy);
        reservation.setPickupDeadline(NOW.plusDays(1));
        when(reservations.findBookIdForCancellation(21L)).thenReturn(Optional.of(7L));
        when(reservations.findForCancellation(21L)).thenReturn(Optional.of(reservation));
    }

    @Test
    void noFeeAndFullyPaidFeesDoNotBlockReader() {
        assertThat(service.readerEligibility("TV-0012", 3L).eligible()).isTrue();
        assertThat(service.readerEligibility("TV-0012", 3L).blockReasons()).isEmpty();
        verify(loans, times(2)).sumUnpaidFeesForReader(12L);
    }

    @Test
    void unpaidBalanceFormatsVndAndBlocksDirectLoanPreview() {
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(new BigDecimal("1234567"));
        var eligibility = service.readerEligibility("TV-0012", 3L);
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.remainingBooks()).isZero();
        assertThat(eligibility.reasonCode()).isEqualTo("LOAN_UNPAID_FEES");
        assertThat(eligibility.message()).contains("1.234.567 ₫");
        assertThat(eligibility.blockReasons()).extracting(r -> r.code())
                .containsExactly("LOAN_UNPAID_FEES");
    }

    @Test
    void debtStillAppearsAlongsideExistingCardOrOverdueBlocks() {
        card.setStatus("LOCKED");
        when(loans.countOverdueUnreturnedLoansForReader(eq(12L), any())).thenReturn(2L);
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(new BigDecimal("50000"));
        var result = service.readerEligibility("TV-0012", 3L);
        assertThat(result.blockReasons()).extracting(r -> r.code())
                .containsExactly("LIBRARY_CARD_LOCKED", "LOAN_OVERDUE_UNRETURNED", "LOAN_UNPAID_FEES");
        assertThat(result.message()).contains("50.000 ₫", "2 phiếu mượn quá hạn");
    }

    @Test
    void debtAtConfirmationRejectsBeforeAnyDirectLoanWrite() {
        // Even if the operator already scanned an AVAILABLE copy, the final server check wins.
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(new BigDecimal("200000"));
        var request = new CreateDirectLoanRequest(UUID.randomUUID(), "TV-0012", List.of("BC-031"));
        assertThatThrownBy(() -> service.createDirectLoan(request, 3L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("LOAN_UNPAID_FEES");
                    assertThat(error.getMessage()).contains("200.000 ₫");
                });
        verify(loans, never()).insertDirect(any(), any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }

    @Test
    void reservationConversionCannotBypassUnpaidFees() {
        when(copy.getStatus()).thenReturn("HELD");
        when(loans.sumUnpaidFeesForReader(12L)).thenReturn(new BigDecimal("8500"));
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("LOAN_UNPAID_FEES");
                    assertThat(error.getMessage()).contains("8.500 ₫");
                });
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void afterDebtIsFullySettledRecheckingRestoresEligibility() {
        when(loans.sumUnpaidFeesForReader(12L))
                .thenReturn(new BigDecimal("10000"), new BigDecimal("4000"), BigDecimal.ZERO);
        assertThat(service.readerEligibility("TV-0012", 3L).message()).contains("10.000 ₫");
        assertThat(service.readerEligibility("TV-0012", 3L).message()).contains("4.000 ₫");
        assertThat(service.readerEligibility("TV-0012", 3L).eligible()).isTrue();
    }
}
