package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.bookcopy.BarcodeMode;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCopyServiceTest {
    @Mock BookCopyRepository copies;
    @Mock BookRepository books;
    @Mock ShelfRepository shelves;
    @InjectMocks BookCopyService service;

    private CreateBookCopyRequest manual(String barcode, LocalDate date) {
        return new CreateBookCopyRequest(BarcodeMode.MANUAL, barcode, 10L, 20L, date,
                new BigDecimal("85000.00"), PhysicalCondition.GOOD, null, null);
    }
    private CreateBookCopyRequest auto(LocalDate date) {
        return new CreateBookCopyRequest(BarcodeMode.AUTO, null, 10L, 20L, date,
                new BigDecimal("85000.00"), PhysicalCondition.GOOD, null, null);
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")); }
    private Shelf location() {
        Warehouse warehouse = new Warehouse(); warehouse.setId(10L); warehouse.setCode("KHO-A"); warehouse.setName("Kho A");
        Shelf shelf = new Shelf(); shelf.setId(20L); shelf.setWarehouse(warehouse); shelf.setCode("A01");
        return shelf;
    }
    private BookCopy existing(String barcode, boolean full) {
        Book book = new Book(); book.setId(1L); book.setTitle("Đầu sách A");
        BookCopy copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(100L);
        when(copy.getBarcode()).thenReturn(barcode);
        when(copy.getBook()).thenReturn(book);
        if (full) {
            when(copy.getShelf()).thenReturn(location()); when(copy.getStatus()).thenReturn("AVAILABLE");
            when(copy.getPhysicalCondition()).thenReturn(PhysicalCondition.GOOD);
            when(copy.getReceivedDate()).thenReturn(today()); when(copy.getCoverPrice()).thenReturn(new BigDecimal("85000.00"));
        }
        return copy;
    }
    @Test void createsTrimmedManualBarcodeOnRouteBookAndReturnsAvailable() {
        when(books.existsById(1L)).thenReturn(true);
        BookCopy copy = existing("TV-001", true);
        when(copies.findByBarcode("TV-001")).thenReturn(Optional.empty(), Optional.of(copy));
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(location()));
        when(copies.insertIfBarcodeAbsent(1L, "TV-001", 20L, today(), new BigDecimal("85000.00"), "GOOD")).thenReturn(1);
        var response = service.create(1L, manual(" TV-001 ", today()));
        assertEquals(1L, response.bookId()); assertEquals("AVAILABLE", response.status());
        assertEquals("Sẵn sàng", response.statusLabel()); assertEquals(10L, response.warehouseId());
        assertEquals(20L, response.shelfId()); assertEquals("Tốt", response.physicalConditionLabel());
        verify(copies).insertIfBarcodeAbsent(1L, "TV-001", 20L, today(), new BigDecimal("85000.00"), "GOOD");
        verify(copies, never()).nextAutoBarcodeNumber();
    }
    @Test void autoGeneratesBarcodeFromWarehouseShelfAndGlobalSequence() {
        when(books.existsById(1L)).thenReturn(true);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(location()));
        when(copies.nextAutoBarcodeNumber()).thenReturn(1L);
        when(copies.insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000001", 20L, today(), new BigDecimal("85000.00"), "GOOD")).thenReturn(1);
        BookCopy copy = existing("TV-KHO-A-A01-000001", true);
        when(copies.findByBarcode("TV-KHO-A-A01-000001")).thenReturn(Optional.of(copy));

        var response = service.create(1L, auto(today()));

        assertEquals("TV-KHO-A-A01-000001", response.barcode());
        verify(copies).nextAutoBarcodeNumber();
        verify(copies).insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000001", 20L, today(), new BigDecimal("85000.00"), "GOOD");
    }
    @Test void autoSkipsCodeAlreadyOccupiedByManualEntry() {
        when(books.existsById(1L)).thenReturn(true);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(location()));
        when(copies.nextAutoBarcodeNumber()).thenReturn(1L, 2L);
        when(copies.insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000001", 20L, today(), new BigDecimal("85000.00"), "GOOD")).thenReturn(0);
        when(copies.insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000002", 20L, today(), new BigDecimal("85000.00"), "GOOD")).thenReturn(1);
        BookCopy copy = existing("TV-KHO-A-A01-000002", true);
        when(copies.findByBarcode("TV-KHO-A-A01-000002")).thenReturn(Optional.of(copy));

        var response = service.create(1L, auto(today()));

        assertEquals("TV-KHO-A-A01-000002", response.barcode());
        verify(copies, times(2)).nextAutoBarcodeNumber();
        verify(copies).insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000001", 20L, today(), new BigDecimal("85000.00"), "GOOD");
        verify(copies).insertIfBarcodeAbsent(1L, "TV-KHO-A-A01-000002", 20L, today(), new BigDecimal("85000.00"), "GOOD");
    }
    @Test void autoRejectsClientSuppliedBarcode() {
        when(books.existsById(1L)).thenReturn(true);
        CreateBookCopyRequest request = new CreateBookCopyRequest(BarcodeMode.AUTO, "TV-HACK", 10L, 20L, today(),
                new BigDecimal("85000.00"), PhysicalCondition.GOOD, null, null);
        assertEquals("AUTO_BARCODE_OVERRIDE", assertThrows(ApiException.class,
                () -> service.create(1L, request)).getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).nextAutoBarcodeNumber();
    }
    @Test void duplicateAcrossDifferentBooksIncludesExistingIdentityAndLink() {
        when(books.existsById(2L)).thenReturn(true);
        BookCopy copy = existing("TV-001", false);
        when(copies.findByBarcode("TV-001")).thenReturn(Optional.of(copy));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(2L, manual("TV-001", today())));
        assertEquals("BARCODE_EXISTS", ex.getCode()); assertEquals(409, ex.getStatus().value());
        assertEquals(100L, ex.getDetails().get("existingCopyId")); assertEquals(1L, ex.getDetails().get("bookId"));
        assertEquals("/book-copies/100", ex.getDetails().get("copyUrl"));
        verifyNoInteractions(shelves);
        verify(copies, never()).insertIfBarcodeAbsent(any(), any(), any(), any(), any(), any());
    }
    @Test void concurrentManualDuplicateStillReturnsExistingCopy() {
        when(books.existsById(1L)).thenReturn(true);
        BookCopy copy = existing("TV-001", false);
        when(copies.findByBarcode("TV-001")).thenReturn(Optional.empty(), Optional.of(copy));
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(location()));
        when(copies.insertIfBarcodeAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);
        assertEquals("BARCODE_EXISTS", assertThrows(ApiException.class,
                () -> service.create(1L, manual("TV-001", today()))).getCode());
    }
    @Test void rejectsFutureDateBeforeWriting() {
        when(books.existsById(1L)).thenReturn(true);
        assertEquals("INVALID_RECEIVED_DATE", assertThrows(ApiException.class,
                () -> service.create(1L, manual("TV-001", today().plusDays(1)))).getCode());
        verifyNoInteractions(copies, shelves);
    }
    @Test void rejectsShelfFromAnotherWarehouse() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findByBarcode("TV-001")).thenReturn(Optional.empty());
        Shelf shelf = location(); shelf.getWarehouse().setId(99L);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(shelf));
        assertEquals("SHELF_WAREHOUSE_MISMATCH", assertThrows(ApiException.class,
                () -> service.create(1L, manual("TV-001", today()))).getCode());
        verify(copies, never()).insertIfBarcodeAbsent(any(), any(), any(), any(), any(), any());
    }
    @Test void rejectsInactiveShelf() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.findByBarcode("TV-001")).thenReturn(Optional.empty());
        Shelf shelf = location(); shelf.setActive(false);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(shelf));
        assertEquals("LOCATION_INACTIVE", assertThrows(ApiException.class,
                () -> service.create(1L, manual("TV-001", today()))).getCode());
    }
    @Test void missingBookDoesNotWrite() {
        assertEquals("BOOK_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.create(7L, manual("TV-001", today()))).getCode());
        verifyNoInteractions(copies, shelves);
    }
    @Test void listsOnlyCopiesReturnedForRequestedBook() {
        when(books.existsById(1L)).thenReturn(true);
        BookCopy first = existing("TV-001", true);
        BookCopy second = existing("TV-002", true);
        when(copies.findAllByBookIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));

        var result = service.getByBookId(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(copy -> copy.bookId().equals(1L)));
        assertEquals(List.of("TV-001", "TV-002"), result.stream().map(r -> r.barcode()).toList());
        verify(copies).findAllByBookIdOrderByIdAsc(1L);
        verify(copies, never()).findAll();
    }

    @Test void emptyBookCopyListIsReturnedForBookWithoutCopies() {
        when(books.existsById(2L)).thenReturn(true);
        when(copies.findAllByBookIdOrderByIdAsc(2L)).thenReturn(List.of());

        assertTrue(service.getByBookId(2L).isEmpty());
    }

    @Test void listCopiesRejectsMissingBook() {
        when(books.existsById(999L)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.getByBookId(999L));

        assertEquals("BOOK_NOT_FOUND", ex.getCode());
        verify(copies, never()).findAllByBookIdOrderByIdAsc(anyLong());
    }

    @Test void rejectsReassignmentOfExistingCopy() {
        var request = new com.duanttcsn5.library.dto.bookcopy.UpdateBookCopyRequest(
                10L, 20L, PhysicalCondition.GOOD, null, null, 2L, null);
        assertEquals("COPY_FIXED_FIELDS", assertThrows(ApiException.class,
                () -> service.update(100L, request)).getCode());
        verify(copies, never()).save(any());
    }
    @Test void missingCopyReturnsNotFound() {
        assertEquals("COPY_NOT_FOUND", assertThrows(ApiException.class, () -> service.getById(999L)).getCode());
    }
}
