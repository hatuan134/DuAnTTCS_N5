package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.bookcopy.UpdateBookCopyRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCopyUpdateServiceTest {
    @Mock BookCopyRepository copies;
    @Mock BookRepository books;
    @Mock ShelfRepository shelves;
    @InjectMocks BookCopyService service;
    private BookCopy copy;
    private Shelf destination;

    @BeforeEach void setup() {
        Book book = new Book(); book.setId(1L); book.setTitle("Đầu sách A");
        copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", 100L);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", "TV-000001");
        ReflectionTestUtils.setField(copy, "status", "AVAILABLE");
        ReflectionTestUtils.setField(copy, "receivedDate", LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(copy, "coverPrice", new BigDecimal("85000"));
        copy.updateDetails(shelf(10L, 20L), PhysicalCondition.GOOD, "Ghi chú cũ");
        destination = shelf(11L, 21L);
    }
    private Shelf shelf(Long warehouseId, Long shelfId) {
        Warehouse warehouse = new Warehouse(); warehouse.setId(warehouseId);
        warehouse.setCode("KHO-" + warehouseId); warehouse.setName("Kho " + warehouseId);
        Shelf shelf = new Shelf(); shelf.setId(shelfId); shelf.setWarehouse(warehouse); shelf.setCode("KE-" + shelfId);
        return shelf;
    }
    private UpdateBookCopyRequest request(Long warehouseId, Long shelfId, PhysicalCondition condition, String notes) {
        return new UpdateBookCopyRequest(warehouseId, shelfId, condition, notes, null, null, null);
    }
    private void availableDestination() {
        when(copies.findById(100L)).thenReturn(Optional.of(copy));
        when(shelves.findForCopyCreation(21L)).thenReturn(Optional.of(destination));
    }
    @Test void updatesWarehouseShelfConditionAndNotesWithoutChangingIdentityOrOtherFields() {
        availableDestination();
        var result = service.update(100L, request(11L, 21L, PhysicalCondition.OLD, "  Bìa xước\nChuyển kho B  "));
        assertEquals(11L, result.warehouseId()); assertEquals(21L, result.shelfId());
        assertEquals(PhysicalCondition.OLD, result.physicalCondition());
        assertEquals("Bìa xước\nChuyển kho B", result.notes());
        assertEquals("TV-000001", result.barcode()); assertEquals(1L, result.bookId());
        assertEquals("AVAILABLE", result.status());
        assertEquals(LocalDate.of(2026, 9, 1), result.receivedDate());
        assertEquals(new BigDecimal("85000"), result.coverPrice());
        verify(copies).flush(); verifyNoInteractions(books);
    }
    @Test void changesShelfWithinSameWarehouse() {
        destination.getWarehouse().setId(10L); availableDestination();
        var result = service.update(100L, request(10L, 21L, PhysicalCondition.GOOD, "Ghi chú cũ"));
        assertEquals(10L, result.warehouseId()); assertEquals(21L, result.shelfId());
    }
    @Test void changesOnlyNotesOrConditionAndAllowsClearingNotes() {
        when(copies.findById(100L)).thenReturn(Optional.of(copy));
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(copy.getShelf()));
        for (PhysicalCondition condition : PhysicalCondition.values()) {
            var result = service.update(100L, request(10L, 20L, condition, "Ghi chú mới"));
            assertEquals(condition, result.physicalCondition()); assertEquals("Ghi chú mới", result.notes());
            assertEquals(20L, result.shelfId()); assertEquals("AVAILABLE", result.status());
        }
        assertNull(service.update(100L, request(10L, 20L, PhysicalCondition.GOOD, "   ")).notes());
        assertNull(service.update(100L, request(10L, 20L, PhysicalCondition.GOOD, null)).notes());
    }
    @Test void rejectsChangingWarehouseWhileKeepingShelfOfOldWarehouseAtomically() {
        availableDestination();
        var ex = assertThrows(ApiException.class, () -> service.update(100L, request(99L, 21L, PhysicalCondition.OLD, "Không lưu")));
        assertEquals("SHELF_WAREHOUSE_MISMATCH", ex.getCode());
        assertEquals(20L, copy.getShelf().getId()); assertEquals("Ghi chú cũ", copy.getNotes());
        verify(copies, never()).flush();
    }
    @Test void rejectsInactiveShelfAndWarehouse() {
        availableDestination(); destination.setActive(false);
        assertEquals("LOCATION_INACTIVE", assertThrows(ApiException.class,
                () -> service.update(100L, request(11L, 21L, PhysicalCondition.GOOD, null))).getCode());
        destination.setActive(true); destination.getWarehouse().setActive(false);
        assertEquals("LOCATION_INACTIVE", assertThrows(ApiException.class,
                () -> service.update(100L, request(11L, 21L, PhysicalCondition.GOOD, null))).getCode());
        verify(copies, never()).flush();
    }
    @Test void rejectsUnknownShelfAndMissingCopy() {
        assertEquals("COPY_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.update(999L, request(11L, 21L, PhysicalCondition.GOOD, null))).getCode());
        when(copies.findById(100L)).thenReturn(Optional.of(copy));
        assertEquals("SHELF_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.update(100L, request(11L, 21L, PhysicalCondition.GOOD, null))).getCode());
        verify(copies, never()).flush();
    }
    @Test void rejectsBarcodeBookOrStatusOverridesBeforeAnyWrite() {
        for (var request : new UpdateBookCopyRequest[]{
                new UpdateBookCopyRequest(11L, 21L, PhysicalCondition.OLD, null, "TV-HACK", null, null),
                new UpdateBookCopyRequest(11L, 21L, PhysicalCondition.OLD, null, "", null, null),
                new UpdateBookCopyRequest(11L, 21L, PhysicalCondition.OLD, null, null, 2L, null),
                new UpdateBookCopyRequest(11L, 21L, PhysicalCondition.OLD, null, null, null, "REPAIR")}) {
            assertEquals("COPY_FIXED_FIELDS", assertThrows(ApiException.class, () -> service.update(100L, request)).getCode());
        }
        assertEquals("TV-000001", copy.getBarcode()); verifyNoInteractions(copies, shelves);
    }
    @Test void rejectsInvalidLocationConditionAndLongNotes() {
        for (var request : new UpdateBookCopyRequest[]{
                request(null, 21L, PhysicalCondition.GOOD, null), request(11L, 0L, PhysicalCondition.GOOD, null),
                request(-1L, 21L, PhysicalCondition.GOOD, null), request(11L, 21L, null, null)}) {
            assertEquals("INVALID_COPY_DETAILS", assertThrows(ApiException.class, () -> service.update(100L, request)).getCode());
        }
        assertEquals("NOTES_TOO_LONG", assertThrows(ApiException.class,
                () -> service.update(100L, request(11L, 21L, PhysicalCondition.GOOD, "x".repeat(2001)))).getCode());
        verifyNoInteractions(copies, shelves);
    }
}
