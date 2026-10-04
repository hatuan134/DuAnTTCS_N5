package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
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
    BookReservation target;

    @BeforeEach void setup() {
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        Book book = new Book(); book.setId(7L);
        target = new BookReservation(book, reader, "PENDING"); target.setId(100L);
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(reservations.findBookIdForCancellation(100L)).thenReturn(Optional.of(7L));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(reservations.findForCancellation(100L)).thenReturn(Optional.of(target));
    }

    @Test void cancelsOnlyPendingWithoutTouchingCopiesCardsOrConfiguration() {
        var time = target.getReservedAt();
        service.cancelMine(100L, 12L);
        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        assertThat(target.getReservedAt()).isEqualTo(time);
        var order = inOrder(books, reservations);
        order.verify(reservations).findBookIdForCancellation(100L);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).findForCancellation(100L);
        order.verify(reservations).saveAndFlush(target);
        verifyNoInteractions(copies, cards, configuration);
    }

    @Test void anotherReadersOrderIsHiddenAndUnchanged() {
        User other = new User(); other.setId(99L); target.setReader(other);
        assertThatThrownBy(() -> service.cancelMine(100L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(target.getStatus()).isEqualTo("PENDING");
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void rechecksCurrentStateAndRejectsRepeatOrAllocatedOrder() {
        for (String state : new String[]{"READY_FOR_PICKUP", "CANCELLED", "FULFILLED", "EXPIRED"}) {
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
}
