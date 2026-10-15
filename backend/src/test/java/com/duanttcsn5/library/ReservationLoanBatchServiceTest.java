package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationLoanBatchServiceTest {
    private final OffsetDateTime at = OffsetDateTime.parse("2026-10-12T10:00:00+07:00");
    private final BookRepository books = mock(BookRepository.class);
    private final BookReservationRepository reservations = mock(BookReservationRepository.class);
    private final BookCopyRepository copies = mock(BookCopyRepository.class);
    private final LibraryCardRepository cards = mock(LibraryCardRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final LibraryConfigurationService configuration = mock(LibraryConfigurationService.class);
    private final List<BookReservation> selected = new ArrayList<>();
    private LoanService service;
    private CardType type;

    @BeforeEach void setup() {
        when(loans.nextLoanNumber()).thenReturn("PM-000001");
        User staff = new User(); staff.setId(3L); staff.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); staff.setRole(role);
        when(users.findById(3L)).thenReturn(Optional.of(staff));
        User reader = new User(); reader.setId(12L); reader.setFullName("Bạn đọc mẫu");
        type = new CardType(); type.setName("Sinh viên"); type.setMaxBooks(5); type.setLoanDays(14);
        LibraryCard card = new LibraryCard(); card.setUser(reader); card.setCardNumber("TV-12"); card.setCardType(type);
        card.setIssuedAt(at.toLocalDate().minusDays(30)); card.setExpiresAt(at.toLocalDate().plusDays(90));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        var due = at.toLocalDate().plusDays(14);
        var dates = new LoanDatePreviewResponse(at.toLocalDate(), "Sinh viên", 14, due, due,
                due.atTime(17, 0).atOffset(ZoneOffset.ofHours(7)), false, List.of());
        when(configuration.calculateLoanDates(any(), eq(14), eq("Sinh viên"))).thenReturn(dates);
        for (long id = 21; id <= 23; id++) {
            Book book = new Book(); book.setId(id); book.setTitle("Sách " + id);
            BookCopy copy = mock(BookCopy.class); when(copy.getId()).thenReturn(id + 10);
            when(copy.getBook()).thenReturn(book); when(copy.getStatus()).thenReturn("HELD");
            when(copy.getBarcode()).thenReturn("LIB-" + id);
            BookReservation hold = new BookReservation(book, reader, "READY_FOR_PICKUP");
            hold.setId(id); hold.setBookCopy(copy); hold.setReservedAt(at.minusDays(1)); hold.setPickupDeadline(at.plusDays(2));
            selected.add(hold);
            when(loans.findReservationReaderId(id)).thenReturn(Optional.of(12L));
            when(reservations.findBookIdForCancellation(id)).thenReturn(Optional.of(id));
            when(books.findForReservation(id)).thenReturn(Optional.of(book));
            when(reservations.findForCancellation(id)).thenReturn(Optional.of(hold));
            when(copies.findForStatusChange(id + 10)).thenReturn(Optional.of(copy));
        }
        when(loans.insert(eq(21L), eq(12L), eq(3L), anyString(), any())).thenReturn(81L);
        service = new LoanService(books, reservations, copies, cards, users, loans, configuration,
                Clock.fixed(at.toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private CreateReservationLoanBatchRequest request(List<Long> ids) {
        return new CreateReservationLoanBatchRequest(ids, new CreateReservationLoanRequest("TV-12"));
    }

    private void rejected(String code, CreateReservationLoanBatchRequest request) {
        assertThatThrownBy(() -> service.createFromReservations(request, 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertReservationItem(any(), any(), any(), any(), any());
    }

    @Test void threeReadyHoldsCreateOneHeaderAndThreeItems() {
        var result = service.createFromReservations(request(List.of(23L, 21L, 22L)), 3L);
        assertThat(result.items()).hasSize(3).allMatch(item -> item.id().equals(81L)
                && item.loanNumber().equals(result.loanNumber()) && item.borrowedAt().equals(at));
        verify(loans, times(1)).insert(eq(21L), eq(12L), eq(3L), anyString(), eq(at));
        verify(loans).insertItem(eq(81L), eq(31L), eq(at), any());
        verify(loans).insertReservationItem(eq(81L), eq(32L), eq(at), any(), eq(22L));
        verify(loans).insertReservationItem(eq(81L), eq(33L), eq(at), any(), eq(23L));
        assertThat(selected).allMatch(hold -> "FULFILLED".equals(hold.getStatus()));
    }

    @Test void twoSelectedHoldsLeaveUnselectedHoldReady() {
        assertThat(service.createFromReservations(request(List.of(21L, 22L)), 3L).items()).hasSize(2);
        assertThat(selected.get(2).getStatus()).isEqualTo("READY_FOR_PICKUP");
    }

    @Test void quotaCountsAllSelectedBooksBeforeWriting() {
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(3L);
        rejected("LOAN_DRAFT_LIMIT_EXCEEDED", request(List.of(21L, 22L, 23L)));
    }

    @Test void mixedReadersAreRejectedBeforeLocking() {
        when(loans.findReservationReaderId(22L)).thenReturn(Optional.of(99L));
        rejected("RESERVATION_READER_MISMATCH", request(List.of(21L, 22L)));
        verify(reservations, never()).lockReaderForCreation(any());
    }

    @Test void expiredSiblingDoesNotConvertTheFirstHold() {
        selected.get(1).setPickupDeadline(at.minusSeconds(1));
        rejected("RESERVATION_PICKUP_EXPIRED", request(List.of(21L, 22L)));
        assertThat(selected.get(0).getStatus()).isEqualTo("READY_FOR_PICKUP");
    }

    @Test void pendingSiblingCannotBeIncluded() {
        selected.get(1).setStatus("PENDING");
        rejected("RESERVATION_NOT_READY", request(List.of(21L, 22L)));
    }

    @Test void wrongCardAndDuplicateIdsAreRejected() {
        rejected("INVALID_RESERVATION_SELECTION", request(List.of(21L, 21L)));
        rejected("RESERVATION_CARD_MISMATCH", new CreateReservationLoanBatchRequest(
                List.of(21L, 22L), new CreateReservationLoanRequest("TV-WRONG")));
    }

    @Test void secondConfirmationCannotCreateAnotherHeader() {
        selected.get(0).setStatus("FULFILLED");
        rejected("RESERVATION_ALREADY_CONVERTED", request(List.of(21L, 22L)));
    }
}
