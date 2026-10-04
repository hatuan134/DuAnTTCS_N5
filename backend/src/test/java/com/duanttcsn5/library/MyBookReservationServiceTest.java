package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyBookReservationServiceTest {
    private final BookReservationRepository reservations = mock(BookReservationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LibraryCardRepository cards = mock(LibraryCardRepository.class);
    private final BookReservationService service = new BookReservationService(mock(BookRepository.class),
            reservations, users, cards, mock(BookCopyRepository.class), mock(LibraryConfigurationService.class));
    private User reader;

    @BeforeEach void setup() {
        reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(reader));
    }

    private BookReservation order(Long id, String status, boolean allocated) {
        Book book = new Book(); book.setId(7L); book.setTitle("Lập trình Java");
        BookReservation r = new BookReservation(book, reader, status); r.setId(id);
        r.setReservedAt(OffsetDateTime.parse("2030-01-01T08:00:00+07:00"));
        r.setPickupDeadline(OffsetDateTime.parse("2030-01-04T17:00:00+07:00"));
        if (allocated) r.setBookCopy(new BookCopy());
        return r;
    }

    @Test void emptyListAndNoCardRequirement() {
        when(reservations.findAllForReader(12L)).thenReturn(List.of());
        assertThat(service.getMyReservations(12L)).isEmpty();
        verifyNoInteractions(cards);
    }

    @Test void pendingUsesGlobalQueueCountAndHidesDeadline() {
        var pending = order(100L, "PENDING", false);
        when(reservations.findAllForReader(12L)).thenReturn(List.of(pending));
        when(reservations.findPendingQueuePosition(100L)).thenReturn(3L);
        var result = service.getMyReservations(12L).get(0);
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.bookTitle()).isEqualTo("Lập trình Java");
        assertThat(result.reservedAt()).isEqualTo(pending.getReservedAt());
        assertThat(result.queuePosition()).isEqualTo(3L);
        assertThat(result.pickupDeadline()).isNull();
        verify(reservations).findAllForReader(12L);
    }

    @Test void readyHasExactDeadlineOnlyWhenAllocatedAndHistoryHasNeitherField() {
        var ready = order(1L, "READY_FOR_PICKUP", true);
        when(reservations.findAllForReader(12L)).thenReturn(List.of(ready,
                order(2L, "READY_FOR_PICKUP", false), order(3L, "CANCELLED", true),
                order(4L, "FULFILLED", true), order(5L, "EXPIRED", true)));
        var result = service.getMyReservations(12L);
        assertThat(result).hasSize(5);
        assertThat(result.get(0).pickupDeadline()).isEqualTo(ready.getPickupDeadline());
        assertThat(result).allMatch(r -> r.queuePosition() == null);
        assertThat(result.subList(1, 5)).allMatch(r -> r.pickupDeadline() == null);
        verify(reservations, never()).findPendingQueuePosition(anyLong());
    }

    @Test void rejectsMissingIdentityMissingUserWrongRoleAndInactiveUser() {
        assertThatThrownBy(() -> service.getMyReservations(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.getMyReservations(999L)).isInstanceOf(ApiException.class);
        reader.getRole().setCode("ADMIN");
        assertThatThrownBy(() -> service.getMyReservations(12L)).isInstanceOf(ApiException.class);
        reader.getRole().setCode("READER"); reader.setStatus("DISABLED");
        assertThatThrownBy(() -> service.getMyReservations(12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(reservations);
    }
}
