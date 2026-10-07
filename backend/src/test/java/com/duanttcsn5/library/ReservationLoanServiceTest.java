package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationLoanServiceTest {
    private BookRepository books;
    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private LibraryCardRepository cards;
    private UserRepository users;
    private LoanRepository loans;
    private LoanService service;
    private BookReservation reservation;
    private BookCopy copy;
    private LibraryCard card;
    private User actor;

    @BeforeEach
    void setup() {
        books = mock(BookRepository.class); reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class); cards = mock(LibraryCardRepository.class);
        users = mock(UserRepository.class); loans = mock(LoanRepository.class);
        service = new LoanService(books, reservations, copies, cards, users, loans);
        actor = new User(); actor.setId(3L); actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); actor.setRole(role);
        when(users.findById(3L)).thenReturn(Optional.of(actor));
        Book book = new Book(); book.setId(7L); book.setTitle("Mắt biếc");
        User reader = new User(); reader.setId(12L); reader.setFullName("Nguyễn Văn An");
        copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(31L); when(copy.getBook()).thenReturn(book);
        when(copy.getBarcode()).thenReturn("LIB-031"); when(copy.getStatus()).thenReturn("HELD");
        reservation = new BookReservation(book, reader, "READY_FOR_PICKUP");
        reservation.setId(21L); reservation.setBookCopy(copy);
        reservation.setReservedAt(OffsetDateTime.now().minusDays(10));
        reservation.setPickupDeadline(OffsetDateTime.now().minusDays(7));
        card = new LibraryCard(); card.setCardNumber("TV-0012"); card.setUser(reader);
        when(reservations.findBookIdForCancellation(21L)).thenReturn(Optional.of(7L));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(reservations.findForCancellation(21L)).thenReturn(Optional.of(reservation));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        when(copies.findForStatusChange(31L)).thenReturn(Optional.of(copy));
        when(loans.insert(eq(21L), eq(12L), eq(3L), anyString(), any())).thenReturn(81L);
    }

    private void rejected(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any());
    }

    @Test
    void validCardCreatesExactReaderAndHeldCopyWithoutCalculatingDueDateOrRejectingPastDeadline() {
        var result = service.createFromReservation(21L, 3L, "  TV-0012  ");
        assertThat(result.id()).isEqualTo(81L); assertThat(result.reservationId()).isEqualTo(21L);
        assertThat(result.readerId()).isEqualTo(12L); assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.cardNumber()).isEqualTo("TV-0012"); assertThat(result.copyId()).isEqualTo(31L);
        assertThat(result.barcode()).isEqualTo("LIB-031"); assertThat(result.bookId()).isEqualTo(7L);
        assertThat(result.loanNumber()).matches("PM-[0-9A-F-]{36}");
        var ordered = inOrder(books, reservations, copies, loans);
        ordered.verify(books).findForReservation(7L);
        ordered.verify(reservations).findForCancellation(21L);
        ordered.verify(copies).findForStatusChange(31L);
        ordered.verify(loans).insert(21L, 12L, 3L, result.loanNumber(), result.borrowedAt());
        ordered.verify(loans).insertItem(81L, 31L, result.borrowedAt());
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        verify(reservations, never()).save(any()); verify(copies, never()).save(any());
    }

    @Test void wrongCardCannotCreateAnyLoan() {
        rejected("RESERVATION_CARD_MISMATCH", () -> service.createFromReservation(21L, 3L, "TV-OTHER"));
        verify(copies, never()).findForStatusChange(any());
    }
    @Test void emptyWhitespaceNullAndOversizeCardsFailBeforeLocking() {
        for (String input : new String[]{null, "", "   ", "\u00A0", "x".repeat(101)})
            rejected("INVALID_CARD_NUMBER", () -> service.createFromReservation(21L, 3L, input));
        verifyNoInteractions(books, reservations, copies, cards);
    }
    @Test void repeatedConversionIsRejectedEvenWhenReservationIsStillReady() {
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-OLD"));
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void legacyLinkedLoanAlsoBlocksConversion() {
        when(reservations.hasLoanLinkedToReservation(21L)).thenReturn(true);
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void fulfilledReservationCannotBeConvertedAgain() {
        reservation.setStatus("FULFILLED");
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void onlyReadyReservationsCanCreateLoans() {
        for (String status : new String[]{"PENDING", "CANCELLED", "EXPIRED"}) {
            reservation.setStatus(status);
            rejected("RESERVATION_NOT_READY", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        }
    }
    @Test void missingCardAndUnallocatedCopyAreRejected() {
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.empty());
        rejected("LIBRARY_CARD_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        reservation.setBookCopy(null);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void wrongBookNonHeldOrAlreadyBorrowedCopyIsRejected() {
        when(copy.getStatus()).thenReturn("AVAILABLE");
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(copy.getStatus()).thenReturn("HELD"); when(copies.hasUnreturnedLoan(31L)).thenReturn(true);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(copies.hasUnreturnedLoan(31L)).thenReturn(false);
        Book other = new Book(); other.setId(8L); when(copy.getBook()).thenReturn(other);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void readerInactiveStaffAndUnauthenticatedCallAreRejected() {
        actor.getRole().setCode("READER");
        rejected("STAFF_ROLE_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        actor.getRole().setCode("LIBRARIAN"); actor.setStatus("LOCKED");
        rejected("STAFF_ROLE_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        rejected("LOGIN_REQUIRED", () -> service.createFromReservation(21L, null, "TV-0012"));
    }
    @Test void invalidOrMissingReservationUsesExistingErrors() {
        rejected("INVALID_RESERVATION_ID", () -> service.createFromReservation(0L, 3L, "TV-0012"));
        rejected("RESERVATION_NOT_FOUND", () -> service.createFromReservation(999L, 3L, "TV-0012"));
    }
    @Test void contextShowsCardAndPersistedConversionAfterReload() {
        when(reservations.findReadyForPickupById(21L)).thenReturn(Optional.of(reservation));
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-OLD"));
        var context = service.pickupContext(21L);
        assertThat(context.cardNumber()).isEqualTo("TV-0012");
        assertThat(context.converted()).isTrue(); assertThat(context.loanNumber()).isEqualTo("PM-OLD");
    }
}
