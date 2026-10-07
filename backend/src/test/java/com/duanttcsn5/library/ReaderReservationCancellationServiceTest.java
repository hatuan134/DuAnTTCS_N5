package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReaderReservationCancellationServiceTest {
    final BookRepository books = mock(BookRepository.class);
    final BookReservationRepository reservations = mock(BookReservationRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LibraryCardRepository cards = mock(LibraryCardRepository.class);
    final BookCopyRepository copies = mock(BookCopyRepository.class);
    final LibraryConfigurationService configuration = mock(LibraryConfigurationService.class);
    final BookReservationService service = new BookReservationService(books, reservations, users, cards, copies, configuration);
    User reader;
    Book book;
    BookReservation target;

    @BeforeEach void setup() {
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE"); reader.setFullName("Bạn đọc An");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        book = new Book(); book.setId(7L);
        target = new BookReservation(book, reader, "PENDING"); target.setId(100L);
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(reservations.findBookIdForCancellation(100L)).thenReturn(Optional.of(7L));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(reservations.findForCancellation(100L)).thenReturn(Optional.of(target));
    }

    @Test void cancelsPendingWithoutTouchingCopiesCardsOrConfiguration() {
        var time = target.getReservedAt();
        service.cancelMine(100L, 12L);
        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        assertThat(target.getReservedAt()).isEqualTo(time);
        assertThat(target.getCancelledBy()).isEqualTo(12L);
        assertThat(target.getCancelledByName()).isEqualTo("Bạn đọc An");
        assertThat(target.getCancelledAt()).isNotNull();
        assertThat(target.getCancellationReason()).isEqualTo("Bạn đọc tự huỷ đơn.");
        var order = inOrder(books, reservations);
        order.verify(reservations).findBookIdForCancellation(100L);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).findForCancellation(100L);
        order.verify(reservations).saveAndFlush(target);
        verifyNoInteractions(copies, cards, configuration);
    }

    @Test void readyCancellationTransfersExactHeldCopyToFirstPendingWithFreshDeadline() {
        BookCopy copy = heldCopy(101L, "LIB-101");
        target.setStatus("READY_FOR_PICKUP");
        target.setBookCopy(copy);
        target.setPickupDeadline(OffsetDateTime.now().plusDays(2));

        User nextReader = new User(); nextReader.setId(20L); nextReader.setFullName("Bạn đọc Bình");
        BookReservation next = new BookReservation(book, nextReader, "PENDING");
        next.setId(101L); next.setReservedAt(OffsetDateTime.now().minusHours(2));
        when(copies.findForStatusChange(101L)).thenReturn(Optional.of(copy));
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.of(next));
        when(configuration.calculateReservationPickupDeadline(any(OffsetDateTime.class)))
                .thenAnswer(invocation -> ((OffsetDateTime) invocation.getArgument(0)).plusDays(3));

        service.cancelMine(100L, 12L);

        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        assertThat(target.getBookCopy()).isSameAs(copy);
        assertThat(next.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(next.getBookCopy()).isSameAs(copy);
        assertThat(next.getPickupDeadline()).isAfter(OffsetDateTime.now().plusDays(2));
        assertThat(copy.getStatus()).isEqualTo("HELD");
        verify(reservations).saveAndFlush(target);
        verify(reservations).saveAndFlush(next);
        verify(copies, never()).saveAndFlush(copy);
        verifyNoInteractions(cards);
    }

    @Test void readyCancellationWithoutWaiterReleasesCopyToAvailable() {
        BookCopy copy = heldCopy(101L, "LIB-101");
        target.setStatus("READY_FOR_PICKUP");
        target.setBookCopy(copy);
        target.setPickupDeadline(OffsetDateTime.now().plusDays(2));
        when(copies.findForStatusChange(101L)).thenReturn(Optional.of(copy));
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.empty());

        service.cancelMine(100L, 12L);

        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        verify(copies).saveAndFlush(copy);
        verifyNoInteractions(configuration, cards);
    }

    @Test void invalidHeldCopyOrPickupDeadlineKeepsReadyReservationUntouched() {
        BookCopy copy = heldCopy(101L, "LIB-101");
        target.setStatus("READY_FOR_PICKUP");
        target.setBookCopy(copy);
        target.setPickupDeadline(OffsetDateTime.now().plusDays(2));
        when(copies.findForStatusChange(101L)).thenReturn(Optional.of(copy));

        ReflectionTestUtils.setField(copy, "status", "BORROWED");
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("RESERVATION_COPY_CONFLICT"));
        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");

        ReflectionTestUtils.setField(copy, "status", "HELD");
        User nextReader = new User(); nextReader.setId(20L);
        BookReservation next = new BookReservation(book, nextReader, "PENDING");
        next.setId(101L); next.setReservedAt(OffsetDateTime.now().minusHours(1));
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.of(next));
        when(configuration.calculateReservationPickupDeadline(any(OffsetDateTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_PICKUP_DEADLINE"));
        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(next.getStatus()).isEqualTo("PENDING");
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void legacyReadyWithoutAllocatedCopyCanStillBeCancelled() {
        target.setStatus("READY_FOR_PICKUP");
        service.cancelMine(100L, 12L);
        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        verify(reservations).saveAndFlush(target);
        verifyNoInteractions(copies, configuration, cards);
    }

    @Test void anotherReadersOrderIsHiddenAndUnchanged() {
        User other = new User(); other.setId(99L); target.setReader(other);
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(target.getStatus()).isEqualTo("PENDING");
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void fulfilledReservationIsRejectedWithSpecificReasonAndNoMutation() {
        target.setStatus("FULFILLED");

        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getCode()).isEqualTo("RESERVATION_ALREADY_BORROWED");
            assertThat(e.getMessage()).isEqualTo(
                    "Không thể huỷ đơn vì sách đã được nhận và đơn đã chuyển thành phiếu mượn.");
        });

        assertThat(target.getStatus()).isEqualTo("FULFILLED");
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(copies, cards, configuration);
    }

    @Test void loanLinkedReadyReservationIsRejectedBeforeCopyOrQueueMutation() {
        BookCopy copy = heldCopy(101L, "LIB-101");
        target.setStatus("READY_FOR_PICKUP");
        target.setBookCopy(copy);
        target.setPickupDeadline(OffsetDateTime.now().plusDays(2));
        when(copies.findForStatusChange(101L)).thenReturn(Optional.of(copy));
        when(reservations.hasLoanLinkedToReservation(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getCode()).isEqualTo("RESERVATION_ALREADY_BORROWED");
        });

        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(copy.getStatus()).isEqualTo("HELD");
        verify(reservations, never()).findNextPendingForCancellation(anyLong());
        verify(reservations, never()).saveAndFlush(any());
        verify(copies, never()).saveAndFlush(any());
        verify(copies, never()).hasUnreturnedLoan(anyLong());
        verifyNoInteractions(cards, configuration);
    }

    @Test void rechecksCurrentStateAndRejectsTerminalOrInconsistentPendingOrder() {
        for (String state : new String[]{"CANCELLED", "EXPIRED"}) {
            target.setStatus(state);
            assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            assertThat(target.getStatus()).isEqualTo(state);
        }
        target.setStatus("PENDING"); target.setBookCopy(new BookCopy());
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOf(ApiException.class);
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(copies, cards, configuration);
    }

    @Test void rejectsInvalidIdsAndMissingOrder() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.cancelMine(id, 12L)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThatThrownBy(() -> service.cancelMine(999L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void requiresActiveReaderIdentity() {
        assertThatThrownBy(() -> service.cancelMine(100L, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.cancelMine(100L, 999L)).isInstanceOf(ApiException.class);
        reader.getRole().setCode("LIBRARIAN");
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOf(ApiException.class);
        reader.getRole().setCode("READER"); reader.setStatus("DISABLED");
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(reservations, books, copies, cards, configuration);
    }

    private BookCopy heldCopy(long id, String barcode) {
        BookCopy copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", barcode);
        ReflectionTestUtils.setField(copy, "status", "HELD");
        return copy;
    }
}
