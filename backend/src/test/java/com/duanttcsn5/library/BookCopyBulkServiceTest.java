package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.bookcopy.BulkCreateBookCopiesRequest;
import com.duanttcsn5.library.entity.Shelf;
import com.duanttcsn5.library.entity.Warehouse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyLifecycleRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.ShelfRepository;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCopyBulkServiceTest {

    @Mock BookCopyRepository copies;
    @Mock BookRepository books;
    @Mock ShelfRepository shelves;
    @Mock BookCopyLifecycleRepository lifecycle;

    private BookCopyService service;

    @BeforeEach
    void setup() {
        service = new BookCopyService(copies, books, shelves, lifecycle);
    }

    @Test
    void createsOneCopy() {
        assertSuccessfulBatch(1);
    }

    @Test
    void createsTenCopies() {
        assertSuccessfulBatch(10);
    }

    @Test
    void createsFiftyCopies() {
        assertSuccessfulBatch(50);
    }

    @Test
    void rejectsZeroQuantity() {
        when(books.existsById(1L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("0", today())));

        assertEquals("INVALID_BULK_QUANTITY", exception.getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsQuantityAboveFifty() {
        when(books.existsById(1L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("51", today())));

        assertEquals("INVALID_BULK_QUANTITY", exception.getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsNonIntegerQuantity() {
        when(books.existsById(1L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10.5", today())));

        assertEquals("INVALID_BULK_QUANTITY", exception.getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsMissingWarehouseOrShelfDefensively() {
        when(books.existsById(1L)).thenReturn(true);

        var missingWarehouse = new BulkCreateBookCopiesRequest(
                BigDecimal.TEN, null, 20L, today());
        var missingShelf = new BulkCreateBookCopiesRequest(
                BigDecimal.TEN, 10L, null, today());

        assertEquals("INVALID_BULK_LOCATION", assertThrows(ApiException.class,
                () -> service.createBulk(1L, missingWarehouse)).getCode());
        assertEquals("INVALID_BULK_LOCATION", assertThrows(ApiException.class,
                () -> service.createBulk(1L, missingShelf)).getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsFutureReceivedDate() {
        when(books.existsById(1L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10", today().plusDays(1))));

        assertEquals("INVALID_RECEIVED_DATE", exception.getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsShelfFromAnotherWarehouse() {
        when(books.existsById(1L)).thenReturn(true);
        Shelf shelf = location();
        shelf.getWarehouse().setId(99L);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(shelf));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10", today())));

        assertEquals("SHELF_WAREHOUSE_MISMATCH", exception.getCode());
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsInactiveLocation() {
        when(books.existsById(1L)).thenReturn(true);
        Shelf shelf = location();
        shelf.setActive(false);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(shelf));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10", today())));

        assertEquals("LOCATION_INACTIVE", exception.getCode());
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsMissingBookBeforeWriting() {
        when(books.existsById(999L)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(999L, request("10", today())));

        assertEquals("BOOK_NOT_FOUND", exception.getCode());
        verifyNoInteractions(shelves);
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void rejectsConcurrentConflictOutsideTheConfirmedPreview() {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(1L);
        when(copies.enableBulkBookCopyCreation()).thenReturn("true");
        when(copies.nextAutoBarcodeNumber()).thenReturn(1L);
        when(copies.insertBulkGeneratedCopy(1L, "TV-000001", 20L, today())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10", today())));

        assertEquals("BULK_PREVIEW_STALE", exception.getCode());
        verify(copies, times(1)).insertBulkGeneratedCopy(anyLong(), any(), anyLong(), any());
    }

    @Test
    void previewsOneTenAndChangedQuantityWithoutConsumingSequence() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.peekAutoBarcodeNumber()).thenReturn(21L, 21L, 35L);
        var one = service.previewBulk(1L, BigDecimal.ONE);
        assertEquals("TV-000021", one.startBarcode());
        assertEquals("TV-000021", one.endBarcode());
        assertEquals(1, one.quantity());
        var ten = service.previewBulk(1L, BigDecimal.TEN);
        assertEquals("TV-000030", ten.endBarcode());
        assertEquals(10, ten.quantity());
        assertEquals("TV-000039", service.previewBulk(1L, BigDecimal.valueOf(5)).endBarcode());
        verify(copies, never()).nextAutoBarcodeNumber();
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void requiresExplicitConfirmation() {
        prepareValidLocation();
        var unconfirmed = new BulkCreateBookCopiesRequest(BigDecimal.TEN, 10L, 20L, today());
        assertEquals("BULK_CONFIRMATION_REQUIRED", assertThrows(ApiException.class,
                () -> service.createBulk(1L, unconfirmed)).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
    }

    @Test
    void rejectsStalePreviewWithoutConsumingSequence() {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(11L);
        assertEquals("BULK_PREVIEW_STALE", assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("10", today()))).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void previewRejectsInvalidQuantityAndExhaustedRange() {
        when(books.existsById(1L)).thenReturn(true);
        for (String value : new String[]{"0", "51", "1.5"}) {
            assertEquals("INVALID_BULK_QUANTITY", assertThrows(ApiException.class,
                    () -> service.previewBulk(1L, new BigDecimal(value))).getCode());
        }
        when(copies.peekAutoBarcodeNumber()).thenReturn(999995L);
        assertEquals("BARCODE_SEQUENCE_EXHAUSTED", assertThrows(ApiException.class,
                () -> service.previewBulk(1L, BigDecimal.TEN)).getCode());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "1", "3", "1,2,3", "2,4,6"})
    void skipsGlobalDuplicatesAndStillCreatesExactlyFive(String occupiedNumbers) {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(1L);
        var occupied = new java.util.HashSet<String>();
        if (!occupiedNumbers.isEmpty()) {
            for (String value : occupiedNumbers.split(",")) occupied.add(barcode(Long.parseLong(value)));
        }
        when(copies.existsByBarcode(any())).thenAnswer(call -> occupied.contains(call.getArgument(0)));
        var preview = service.previewBulk(1L, BigDecimal.valueOf(5));
        var expectedSkipped = occupied.stream().sorted().toList();
        assertEquals(expectedSkipped, preview.skippedBarcodes());
        var expectedCreated = new java.util.ArrayList<String>();
        for (long number = 1; expectedCreated.size() < 5; number++) {
            if (!occupied.contains(barcode(number))) expectedCreated.add(barcode(number));
        }
        assertEquals(expectedCreated.get(0), preview.startBarcode());
        assertEquals(expectedCreated.get(4), preview.endBarcode());
        verify(copies, never()).nextAutoBarcodeNumber();
        verify(copies, never()).insertBulkGeneratedCopy(anyLong(), any(), anyLong(), any());

        AtomicLong sequence = new AtomicLong(1);
        when(copies.nextAutoBarcodeNumber()).thenAnswer(call -> sequence.getAndIncrement());
        when(copies.insertBulkGeneratedCopy(eq(1L), any(), eq(20L), eq(today()))).thenReturn(1);
        var result = service.createBulk(1L, new BulkCreateBookCopiesRequest(BigDecimal.valueOf(5),
                10L, 20L, today(), true, preview.startNumber(), preview.skippedBarcodes()));
        assertEquals(5, result.createdCount());
        assertEquals(expectedSkipped, result.skippedBarcodes());
        assertEquals(preview.startBarcode(), result.startBarcode());
        assertEquals(preview.endBarcode(), result.endBarcode());
        assertEquals(expectedCreated,
                result.createdCopies().stream().map(item -> item.barcode()).toList());
        result.createdCopies().forEach(item -> {
            assertEquals(1L, item.bookId());
            assertEquals(10L, item.warehouseId());
            assertEquals("KHO-A", item.warehouseCode());
            assertEquals(20L, item.shelfId());
            assertEquals("A01", item.shelfCode());
            assertEquals(today(), item.receivedDate());
        });
        var codes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(copies, times(5)).insertBulkGeneratedCopy(eq(1L), codes.capture(), eq(20L), eq(today()));
        assertEquals(expectedCreated, codes.getAllValues());
        assertEquals(5, new java.util.HashSet<>(codes.getAllValues()).size());
        verify(copies, times(5 + expectedSkipped.size())).nextAutoBarcodeNumber();
    }

    @Test
    void detectsNewDuplicateEvenWhenSequenceCursorDoesNotChange() {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(1L);
        // Every inspected barcode has a defined result; only this newly occupied code is a duplicate.
        when(copies.existsByBarcode(any())).thenAnswer(call ->
                "TV-000003".equals(call.getArgument(0)));
        assertEquals("BULK_PREVIEW_STALE", assertThrows(ApiException.class,
                () -> service.createBulk(1L, request("5", today()))).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void detectsRemovedDuplicateInConfirmedList() {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(1L);
        var request = new BulkCreateBookCopiesRequest(BigDecimal.ONE, 10L, 20L, today(), true,
                1L, java.util.List.of("TV-000001"));
        assertEquals("BULK_PREVIEW_STALE", assertThrows(ApiException.class,
                () -> service.createBulk(1L, request)).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
    }

    @Test
    void requiresSkippedListSoAnOldClientCannotConfirmUnseenDuplicates() {
        prepareValidLocation();
        var request = new BulkCreateBookCopiesRequest(BigDecimal.ONE, 10L, 20L, today(), true, 1L, null);
        assertEquals("BULK_CONFIRMATION_REQUIRED", assertThrows(ApiException.class,
                () -> service.createBulk(1L, request)).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
    }

    @Test
    void duplicatesExhaustTheRemainingRangeBeforeAnyWrite() {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(999998L);
        when(copies.existsByBarcode("TV-999998")).thenReturn(true);
        var request = new BulkCreateBookCopiesRequest(BigDecimal.valueOf(2), 10L, 20L, today(), true,
                999998L, java.util.List.of("TV-999998"));
        assertEquals("BARCODE_SEQUENCE_EXHAUSTED", assertThrows(ApiException.class,
                () -> service.createBulk(1L, request)).getCode());
        verify(copies, never()).nextAutoBarcodeNumber();
        verify(copies, never()).enableBulkBookCopyCreation();
    }

    @Test
    void allowsLastAvailableBarcodeAfterSkippingDuplicate() {
        when(books.existsById(1L)).thenReturn(true);
        when(copies.peekAutoBarcodeNumber()).thenReturn(999998L);
        when(copies.existsByBarcode("TV-999998")).thenReturn(true);
        var preview = service.previewBulk(1L, BigDecimal.ONE);
        assertEquals("TV-999999", preview.startBarcode());
        assertEquals("TV-999999", preview.endBarcode());
        assertEquals(java.util.List.of("TV-999998"), preview.skippedBarcodes());
    }

    private String barcode(long value) {
        return String.format(java.util.Locale.ROOT, "TV-%06d", value);
    }

    private void assertSuccessfulBatch(int quantity) {
        prepareValidLocation();
        when(copies.peekAutoBarcodeNumber()).thenReturn(1L);
        when(copies.enableBulkBookCopyCreation()).thenReturn("true");
        AtomicLong sequence = new AtomicLong(1);
        when(copies.nextAutoBarcodeNumber()).thenAnswer(invocation -> sequence.getAndIncrement());
        when(copies.insertBulkGeneratedCopy(eq(1L), any(), eq(20L), eq(today()))).thenReturn(1);

        var response = service.createBulk(1L, request(Integer.toString(quantity), today()));

        assertEquals(quantity, response.createdCount());
        assertEquals(quantity, response.createdCopies().size());
        for (int i = 1; i <= quantity; i++) {
            String barcode = String.format(java.util.Locale.ROOT, "TV-%06d", i);
            verify(copies).insertBulkGeneratedCopy(1L, barcode, 20L, today());
            assertEquals(barcode, response.createdCopies().get(i - 1).barcode());
        }
        var order = org.mockito.Mockito.inOrder(copies);
        order.verify(copies).lockAutoBarcodeSequence();
        order.verify(copies).peekAutoBarcodeNumber();
        order.verify(copies).enableBulkBookCopyCreation();
        verify(copies).enableBulkBookCopyCreation();
        verify(copies, times(quantity)).nextAutoBarcodeNumber();
        verify(copies, times(quantity)).insertBulkGeneratedCopy(eq(1L), any(), eq(20L), eq(today()));
    }

    private void prepareValidLocation() {
        when(books.existsById(1L)).thenReturn(true);
        when(shelves.findForCopyCreation(20L)).thenReturn(Optional.of(location()));
    }

    private BulkCreateBookCopiesRequest request(String quantity, LocalDate date) {
        return new BulkCreateBookCopiesRequest(new BigDecimal(quantity), 10L, 20L, date, true, 1L);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private Shelf location() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setCode("KHO-A");
        warehouse.setName("Kho A");
        warehouse.setActive(true);

        Shelf shelf = new Shelf();
        shelf.setId(20L);
        shelf.setWarehouse(warehouse);
        shelf.setCode("A01");
        shelf.setActive(true);
        return shelf;
    }
}
