package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.BookReservation;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadyPickupServiceTest {
    private BookReservationRepository repository;
    private BookReservationService service;

    @BeforeEach
    void setup() {
        repository = mock(BookReservationRepository.class);
        service = new BookReservationService(mock(BookRepository.class), repository,
                mock(UserRepository.class), mock(LibraryCardRepository.class),
                mock(BookCopyRepository.class), mock(LibraryConfigurationService.class));
    }

    private BookReservation ready(Long id, String readerName, String barcode, String deadline) {
        Book book = new Book();
        book.setId(7L); book.setTitle("Mắt biếc");
        User reader = new User();
        reader.setId(12L); reader.setFullName(readerName);
        reader.setEmail("private@example.invalid"); reader.setPasswordHash("private-hash");
        BookReservation reservation = new BookReservation(book, reader, "READY_FOR_PICKUP");
        reservation.setId(id);
        reservation.setReservedAt(OffsetDateTime.parse("2026-10-03T08:00:00+07:00"));
        if (deadline != null) reservation.setPickupDeadline(OffsetDateTime.parse(deadline));
        if (barcode != null) {
            BookCopy copy = mock(BookCopy.class);
            when(copy.getId()).thenReturn(id + 100);
            when(copy.getBarcode()).thenReturn(barcode);
            reservation.setBookCopy(copy);
        }
        return reservation;
    }

    @Test
    void emptyListIsSuccessfulAndReadOnly() {
        when(repository.findReadyForPickup()).thenReturn(List.of());
        assertThat(service.getReadyForPickup()).isEmpty();
        verify(repository).findReadyForPickup();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void singleReservationMapsExactBookCopyReaderAndDeadline() {
        var row = ready(21L, "Nguyễn Văn An", "LIB-021", "2026-10-06T17:00:00+07:00");
        when(repository.findReadyForPickup()).thenReturn(List.of(row));
        var result = service.getReadyForPickup().get(0);
        assertThat(result.id()).isEqualTo(21L);
        assertThat(result.bookId()).isEqualTo(7L);
        assertThat(result.bookTitle()).isEqualTo("Mắt biếc");
        assertThat(result.copyId()).isEqualTo(121L);
        assertThat(result.barcode()).isEqualTo("LIB-021");
        assertThat(result.readerId()).isEqualTo(12L);
        assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.status()).isEqualTo("READY_FOR_PICKUP");
        assertThat(result.pickupDeadline()).isEqualTo(row.getPickupDeadline());
        assertThat(result.reservedAt()).isEqualTo(row.getReservedAt());
        verify(repository).findReadyForPickup();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void preservesDatabaseOrderAndKeepsDistinctCopiesAndReaders() {
        var first = ready(22L, "Trần Bình", "LIB-022", "2026-10-05T12:00:00+07:00");
        var second = ready(21L, "Nguyễn Văn An", "LIB-021", "2026-10-06T17:00:00+07:00");
        when(repository.findReadyForPickup()).thenReturn(List.of(first, second));
        var results = service.getReadyForPickup();
        assertThat(results).extracting(r -> r.id()).containsExactly(22L, 21L);
        assertThat(results).extracting(r -> r.barcode()).containsExactly("LIB-022", "LIB-021");
        assertThat(results).extracting(r -> r.readerName()).containsExactly("Trần Bình", "Nguyễn Văn An");
    }

    @Test
    void legacyReadyRowWithMissingAllocationDoesNotCrashOrDisappear() {
        var row = ready(23L, "Bạn đọc cũ", null, null);
        when(repository.findReadyForPickup()).thenReturn(List.of(row));
        var result = service.getReadyForPickup().get(0);
        assertThat(result.copyId()).isNull();
        assertThat(result.barcode()).isNull();
        assertThat(result.pickupDeadline()).isNull();
        assertThat(result.readerName()).isEqualTo("Bạn đọc cũ");
    }

    @Test
    void detailReturnsOnlyReadyReservation() {
        var row = ready(21L, "Nguyễn Văn An", "LIB-021", "2026-10-06T17:00:00+07:00");
        when(repository.findReadyForPickupById(21L)).thenReturn(Optional.of(row));
        assertThat(service.getReadyForPickupById(21L).barcode()).isEqualTo("LIB-021");
        verify(repository).findReadyForPickupById(21L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingOrChangedReservationUsesVietnameseNotFoundError() {
        when(repository.findReadyForPickupById(21L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReadyForPickupById(21L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(404);
                    assertThat(e.getCode()).isEqualTo("READY_RESERVATION_NOT_FOUND");
                    assertThat(e.getMessage()).contains("đã đổi trạng thái");
                });
    }

    @Test
    void invalidIdRejectsBeforeQuery() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.getReadyForPickupById(id))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("INVALID_RESERVATION_ID");
                    });
        }
        verifyNoInteractions(repository);
    }
}
