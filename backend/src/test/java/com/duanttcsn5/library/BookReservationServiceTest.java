package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookReservationServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Mock BookRepository books;
    @Mock BookReservationRepository reservations;
    @Mock UserRepository users;
    @Mock LibraryCardRepository cards;
    BookReservationService service;
    User reader;
    Book book;
    LibraryCard card;

    @BeforeEach void setup() {
        service = new BookReservationService(books, reservations, users, cards);
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        book = new Book(); book.setId(7L);
        card = new LibraryCard(); card.setUser(reader); card.setStatus("ACTIVE");
        card.setExpiresAt(LocalDate.now(ZONE).plusDays(30));
    }

    private void eligible() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
    }

    private void saved() {
        when(reservations.saveAndFlush(any())).thenAnswer(invocation -> {
            BookReservation reservation = invocation.getArgument(0);
            reservation.setId(100L);
            return reservation;
        });
        when(reservations.findPendingQueuePosition(100L)).thenReturn(3L);
    }

    private void rejected(String code) {
        assertThatThrownBy(() -> service.reserve(7L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
        verifyNoInteractions(reservations);
    }

    @Test void guestCannotReserve() {
        assertThatThrownBy(() -> service.reserve(7L, null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(401);
                    assertThat(error.getMessage()).contains("chưa đăng nhập");
                });
        verifyNoInteractions(books, reservations, users, cards);
    }

    @Test void staffCannotReserveEvenWhenCalledDirectly() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        for (String role : new String[]{"ADMIN", "LIBRARY_MANAGER", "LIBRARIAN"}) {
            reader.getRole().setCode(role);
            rejected("READER_ROLE_REQUIRED");
        }
        verifyNoInteractions(books, cards);
    }

    @Test void inactiveAccountCannotReserve() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        reader.setStatus("LOCKED");
        rejected("ACCOUNT_INACTIVE");
    }

    @Test void expiredDateRejectsEvenIfStatusStillActive() {
        eligible(); card.setExpiresAt(LocalDate.now(ZONE).minusDays(1));
        rejected("LIBRARY_CARD_EXPIRED");
    }

    @Test void expiredStatusRejectsEvenIfDateInFuture() {
        eligible(); card.setStatus("EXPIRED"); rejected("LIBRARY_CARD_EXPIRED");
    }

    @Test void lockedCardRejectsWithSpecificReason() {
        eligible(); card.setStatus("LOCKED"); rejected("LIBRARY_CARD_LOCKED");
    }

    @Test void disabledCardRejects() {
        eligible(); card.setStatus("DISABLED"); rejected("LIBRARY_CARD_INACTIVE");
    }

    @Test void missingCardRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.empty());
        rejected("LIBRARY_CARD_REQUIRED");
    }

    @Test void cardWithoutExpiryRejects() {
        eligible(); card.setExpiresAt(null); rejected("LIBRARY_CARD_INACTIVE");
    }

    @Test void validCardCreatesTitleReservationAndReturnsPosition() {
        eligible(); saved();
        OffsetDateTime before = OffsetDateTime.now(ZONE).minusSeconds(1);
        var response = service.reserve(7L, 12L);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.queuePosition()).isEqualTo(3L);
        assertThat(response.message()).contains("thành công", "3");
        assertThat(response.reservedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(OffsetDateTime.now(ZONE));
        var order = inOrder(books, reservations);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).saveAndFlush(any());
        order.verify(reservations).findPendingQueuePosition(100L);
        verify(reservations).saveAndFlush(argThat(value -> value.getBook() == book
                && value.getReader() == reader && value.getPickupDeadline() == null
                && value.getCancellationReason() == null));
    }

    @Test void expiryTodayIsStillValid() {
        eligible(); saved(); card.setExpiresAt(LocalDate.now(ZONE));
        assertThat(service.reserve(7L, 12L).status()).isEqualTo("PENDING");
    }

    @Test void repeatedReservationsRemainAllowedInThisSlice() {
        eligible(); saved();
        service.reserve(7L, 12L); service.reserve(7L, 12L);
        verify(reservations, times(2)).saveAndFlush(any());
    }

    @Test void invalidBookIdRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        assertThatThrownBy(() -> service.reserve(0L, 12L))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("INVALID_BOOK_ID"));
        verifyNoInteractions(books, reservations, cards);
    }

    @Test void nonexistentBookRejects() {
        when(users.findById(12L)).thenReturn(Optional.of(reader));
        when(books.findForReservation(7L)).thenReturn(Optional.empty());
        rejected("BOOK_NOT_FOUND");
        verifyNoInteractions(cards);
    }
}
