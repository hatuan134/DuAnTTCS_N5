package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.entity.Shelf;
import com.duanttcsn5.library.entity.Warehouse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.ShelfRepository;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCopySummaryServiceTest {
    @Mock BookCopyRepository copies;
    @Mock BookRepository books;
    @Mock ShelfRepository shelves;
    @InjectMocks BookCopyService service;

    @Test void noCopiesReturnsZeroAndEmptyTable() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of());
        var result = service.getSummaryByBookId(1L);
        assertEquals(0L, result.availableCount());
        assertTrue(result.copies().isEmpty());
    }

    @Test void countsEveryCopyWhenAllAreAvailable() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(
                copy(1L, 1L, "AVAILABLE"), copy(2L, 1L, "AVAILABLE"), copy(3L, 1L, "AVAILABLE")));
        var result = service.getSummaryByBookId(1L);
        assertEquals(3L, result.availableCount());
        assertEquals(3, result.copies().size());
    }

    @Test void mixedStatusesCountOnlyAvailableButKeepEveryTableRow() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(
                copy(1L, 1L, "AVAILABLE"), copy(2L, 1L, "BORROWED"),
                copy(3L, 1L, "HELD"), copy(4L, 1L, "REPAIR"),
                copy(5L, 1L, "REMOVED"), copy(6L, 1L, "AVAILABLE"),
                copy(7L, 1L, "LOST"), copy(8L, 1L, "DAMAGED")));
        var result = service.getSummaryByBookId(1L);
        assertEquals(2L, result.availableCount());
        assertEquals(8, result.copies().size());
        assertEquals(List.of("AVAILABLE", "BORROWED", "HELD", "REPAIR", "REMOVED", "AVAILABLE", "LOST", "DAMAGED"),
                result.copies().stream().map(item -> item.status()).toList());
        verify(copies).findAllByBookIdOrderByIdAsc(1L);
        verify(copies, never()).findAll();
    }

    @Test void noAvailableCopiesReturnsZeroWithNonemptyTable() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(
                copy(1L, 1L, "BORROWED"), copy(2L, 1L, "HELD"),
                copy(3L, 1L, "REPAIR"), copy(4L, 1L, "REMOVED")));
        var result = service.getSummaryByBookId(1L);
        assertEquals(0L, result.availableCount());
        assertEquals(4, result.copies().size());
    }

    @Test void rereadsAndRecalculatesWhenStatusChangesInEitherDirection() {
        when(books.existsById(1L)).thenReturn(true);
        BookCopy first = copy(1L, 1L, "AVAILABLE");
        BookCopy second = copy(2L, 1L, "AVAILABLE");
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));
        assertEquals(2L, service.getSummaryByBookId(1L).availableCount());

        for (String status : List.of("BORROWED", "HELD", "REPAIR", "REMOVED")) {
            when(second.getStatus()).thenReturn(status);
            var changed = service.getSummaryByBookId(1L);
            assertEquals(1L, changed.availableCount());
            assertEquals(status, changed.copies().get(1).status());
            when(second.getStatus()).thenReturn("AVAILABLE");
            assertEquals(2L, service.getSummaryByBookId(1L).availableCount());
        }
        verify(copies, times(9)).findAllByBookIdOrderByIdAsc(1L);
    }

    @Test void requestingAnotherBookDoesNotMixCounts() {
        when(books.existsById(1L)).thenReturn(true);
        when(books.existsById(2L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(copy(1L, 1L, "AVAILABLE")));
        when(copies.findAllByBookIdOrderByIdAsc(2L)).thenReturn(List.of(copy(2L, 2L, "HELD")));
        assertEquals(1L, service.getSummaryByBookId(1L).availableCount());
        var result = service.getSummaryByBookId(2L);
        assertEquals(0L, result.availableCount());
        assertTrue(result.copies().stream().allMatch(item -> item.bookId().equals(2L)));
        verify(copies, never()).findAll();
    }

    @Test void missingBookReturnsNotFoundInsteadOfAValidZero() {
        when(books.existsById(999L)).thenReturn(false);
        ApiException error = assertThrows(ApiException.class, () -> service.getSummaryByBookId(999L));
        assertEquals(404, error.getStatus().value());
        assertEquals("BOOK_NOT_FOUND", error.getCode());
        verifyNoInteractions(copies);
    }

    @Test void invalidBookIdsAreRejectedBeforeRepositoryAccess() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            ApiException error = assertThrows(ApiException.class, () -> service.getSummaryByBookId(id));
            assertEquals(400, error.getStatus().value());
            assertEquals("INVALID_BOOK_ID", error.getCode());
        }
        verifyNoInteractions(books, copies, shelves);
    }

    private BookCopy copy(Long id, Long bookId, String status) {
        Book book = new Book();
        book.setId(bookId);
        book.setTitle("Đầu sách " + bookId);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setCode("KHO-A");
        warehouse.setName("Kho A");
        Shelf shelf = new Shelf();
        shelf.setId(20L);
        shelf.setWarehouse(warehouse);
        shelf.setCode("A01");
        BookCopy copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(id);
        when(copy.getBook()).thenReturn(book);
        when(copy.getBarcode()).thenReturn("TEST-SUMMARY-" + id);
        when(copy.getShelf()).thenReturn(shelf);
        when(copy.getStatus()).thenReturn(status);
        return copy;
    }
}
