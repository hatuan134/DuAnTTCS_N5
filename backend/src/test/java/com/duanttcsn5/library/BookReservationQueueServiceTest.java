package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookReservationQueueResponse;
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

class BookReservationQueueServiceTest {
    private BookRepository books;
    private BookReservationRepository repository;
    private BookReservationService service;
    private Book book;

    @BeforeEach
    void setup() {
        books = mock(BookRepository.class);
        repository = mock(BookReservationRepository.class);
        service = new BookReservationService(books, repository, mock(UserRepository.class),
                mock(LibraryCardRepository.class), mock(BookCopyRepository.class),
                mock(LibraryConfigurationService.class));
        book = new Book(); book.setId(7L); book.setTitle("Mắt biếc");
    }

    private void existingBook() { when(books.findById(7L)).thenReturn(Optional.of(book)); }

    private BookReservation row(long id, String status, String name, String time, String barcode) {
        User reader = new User(); reader.setId(id + 100); reader.setFullName(name);
        reader.setEmail("private@example.invalid"); reader.setPasswordHash("private-hash");
        BookReservation reservation = new BookReservation(book, reader, status);
        reservation.setId(id); reservation.setReservedAt(OffsetDateTime.parse(time));
        if (barcode != null) {
            BookCopy copy = mock(BookCopy.class);
            when(copy.getId()).thenReturn(id + 200);
            when(copy.getBarcode()).thenReturn(barcode);
            reservation.setBookCopy(copy);
        }
        return reservation;
    }

    @Test
    void existingBookWithNoReservationsReturnsEmptyQueue() {
        existingBook();
        when(repository.findAllForQueueByBookId(7L)).thenReturn(List.of());
        var result = service.getQueueByBookId(7L);
        assertThat(result.bookId()).isEqualTo(7L);
        assertThat(result.bookTitle()).isEqualTo("Mắt biếc");
        assertThat(result.items()).isEmpty();
        verify(repository).findAllForQueueByBookId(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void onePendingOrderHasPositionOneAndExactReaderAndTime() {
        existingBook();
        var pending = row(21, "PENDING", "Nguyễn Văn An", "2026-10-03T08:01:10+07:00", null);
        when(repository.findAllForQueueByBookId(7L)).thenReturn(List.of(pending));
        var result = service.getQueueByBookId(7L).items().get(0);
        assertThat(result.id()).isEqualTo(21L);
        assertThat(result.readerId()).isEqualTo(121L);
        assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.reservedAt()).isEqualTo(pending.getReservedAt());
        assertThat(result.queuePosition()).isEqualTo(1L);
        assertThat(result.copyId()).isNull();
        assertThat(result.barcode()).isNull();
    }

    @Test
    void fullHistoryPreservesDatabaseOrderAndPositionsCountOnlyPending() {
        existingBook();
        var cancelled = row(20, "CANCELLED", "Bạn đọc cũ", "2026-10-01T08:00:00+07:00", null);
        var ready = row(21, "READY_FOR_PICKUP", "Nguyễn Văn An", "2026-10-01T09:00:00+07:00", "LIB-021");
        var first = row(23, "PENDING", "Trần Bình", "2026-10-02T10:00:00+07:00", null);
        var fulfilled = row(24, "FULFILLED", "Lê Chi", "2026-10-02T11:00:00+07:00", "LIB-024");
        var expired = row(25, "EXPIRED", "Phạm Dũng", "2026-10-02T12:00:00+07:00", null);
        var second = row(22, "PENDING", "Vũ Hà", "2026-10-03T08:00:00+07:00", null);
        when(repository.findAllForQueueByBookId(7L)).thenReturn(List.of(cancelled, ready, first, fulfilled, expired, second));
        var result = service.getQueueByBookId(7L).items();
        assertThat(result).extracting(BookReservationQueueResponse.QueueEntry::id)
                .containsExactly(20L, 21L, 23L, 24L, 25L, 22L);
        assertThat(result).extracting(BookReservationQueueResponse.QueueEntry::queuePosition)
                .containsExactly(null, null, 1L, null, null, 2L);
        assertThat(result.get(1).barcode()).isEqualTo("LIB-021");
        assertThat(result.get(1).copyId()).isEqualTo(221L);
        assertThat(result.get(3).barcode()).isEqualTo("LIB-024");
        verify(repository).findAllForQueueByBookId(7L);
        // No separate position counts, writes or per-row queries.
        verifyNoMoreInteractions(repository);
    }

    @Test
    void positionsUpdateFromFreshSnapshotAfterAnOrderLeavesQueue() {
        existingBook();
        var first = row(21, "PENDING", "Nguyễn Văn An", "2026-10-01T08:00:00+07:00", null);
        var second = row(22, "PENDING", "Trần Bình", "2026-10-02T08:00:00+07:00", null);
        when(repository.findAllForQueueByBookId(7L)).thenReturn(List.of(first, second));
        assertThat(service.getQueueByBookId(7L).items().get(1).queuePosition()).isEqualTo(2L);
        first.setStatus("CANCELLED");
        var refreshed = service.getQueueByBookId(7L).items();
        assertThat(refreshed.get(0).status()).isEqualTo("CANCELLED");
        assertThat(refreshed.get(0).queuePosition()).isNull();
        assertThat(refreshed.get(1).queuePosition()).isEqualTo(1L);
    }

    @Test
    void allNonPendingStatusesRemainVisibleWithoutQueuePositions() {
        existingBook();
        for (String status : new String[]{"READY_FOR_PICKUP", "FULFILLED", "CANCELLED", "EXPIRED"}) {
            when(repository.findAllForQueueByBookId(7L)).thenReturn(List.of(
                    row(21, status, "Nguyễn Văn An", "2026-10-01T08:00:00+07:00", null)));
            var item = service.getQueueByBookId(7L).items().get(0);
            assertThat(item.status()).isEqualTo(status);
            assertThat(item.queuePosition()).isNull();
            assertThat(item.barcode()).isNull();
        }
    }

    @Test
    void invalidBookIdRejectsBeforeDatabaseAccess() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.getQueueByBookId(id)).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus().value()).isEqualTo(400);
                assertThat(e.getCode()).isEqualTo("INVALID_BOOK_ID");
            });
        }
        verifyNoInteractions(books, repository);
    }

    @Test
    void nonexistentBookReturnsNotFoundInsteadOfEmptyQueue() {
        when(books.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getQueueByBookId(7L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(404);
            assertThat(e.getCode()).isEqualTo("BOOK_NOT_FOUND");
        });
        verifyNoInteractions(repository);
    }
}
