package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.bookcopy.RepairBookCopyRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCopyRepairServiceTest {
    @Mock BookCopyRepository copies;
    @Mock BookRepository books;
    @Mock ShelfRepository shelves;
    @Mock BookCopyLifecycleRepository lifecycle;
    @InjectMocks BookCopyService service;
    private BookCopy copy;
    private final UserPrincipal actor = new UserPrincipal(7L, "staff@example.test", "Thủ thư", "LIBRARIAN", 0);
    @BeforeEach void setup() {
        Book book = new Book(); book.setId(1L); book.setTitle("Sách kiểm thử");
        Warehouse warehouse = new Warehouse(); warehouse.setId(1L); warehouse.setCode("K1"); warehouse.setName("Kho 1");
        Shelf shelf = new Shelf(); shelf.setId(1L); shelf.setCode("A1"); shelf.setWarehouse(warehouse);
        copy = new BookCopy();
        ReflectionTestUtils.setField(copy, "id", 10L);
        ReflectionTestUtils.setField(copy, "book", book);
        ReflectionTestUtils.setField(copy, "barcode", "TEST-S2032");
        ReflectionTestUtils.setField(copy, "status", "AVAILABLE");
        copy.updateDetails(shelf, PhysicalCondition.GOOD, "Giữ ghi chú");
    }
    private void found() { when(copies.findForStatusChange(10L)).thenReturn(Optional.of(copy)); }
    private void repair(String reason) { service.repair(10L, new RepairBookCopyRequest(reason), actor); }
    @Test void availableCopyChangesAndWritesTrimmedReasonWithAuthenticatedActor() {
        found();
        var result = service.repair(10L, new RepairBookCopyRequest("  Bong gáy sách  "), actor);
        assertEquals("REPAIR", result.status()); assertEquals("Giữ ghi chú", result.notes());
        var order = inOrder(copies, lifecycle);
        order.verify(copies).findForStatusChange(10L);
        order.verify(lifecycle).hasUnreturnedLoan(10L);
        order.verify(copies).flush();
        order.verify(lifecycle).append(10L, "AVAILABLE", 7L, "Bong gáy sách");
    }
    @Test void rejectsUnreturnedLoanEvenWhenCopyIncorrectlySaysAvailable() {
        found(); when(lifecycle.hasUnreturnedLoan(10L)).thenReturn(true);
        assertEquals("COPY_ON_LOAN", assertThrows(ApiException.class, () -> repair("Bong gáy")).getCode());
        assertEquals("AVAILABLE", copy.getStatus());
        verify(copies, never()).flush(); verify(lifecycle, never()).append(any(), any(), any(), any());
    }
    @Test void borrowedStatusIsAlsoBlockedWhenLegacyCopyHasNoLoanItem() {
        found(); ReflectionTestUtils.setField(copy, "status", "BORROWED");
        assertEquals("COPY_ON_LOAN", assertThrows(ApiException.class, () -> repair("Bong gáy")).getCode());
        assertEquals("BORROWED", copy.getStatus()); verify(copies, never()).flush();
    }
    @Test void rejectsBlankNullAndTooLongReasonsBeforeAnyDatabaseAccess() {
        for (String reason : new String[]{null, "", " \n\t", "x".repeat(2001)}) {
            assertEquals("INVALID_REPAIR_REASON", assertThrows(ApiException.class, () -> repair(reason)).getCode());
        }
        verifyNoInteractions(copies, lifecycle);
    }
    @Test void rejectsOtherStatusesAndDuplicateSubmissionWithoutAddingHistory() {
        found();
        for (String status : new String[]{"HELD", "REPAIR", "REMOVED", "LOST", "DAMAGED"}) {
            ReflectionTestUtils.setField(copy, "status", status);
            assertEquals("COPY_STATUS_NOT_ALLOWED", assertThrows(ApiException.class, () -> repair("Bong gáy")).getCode());
            assertEquals(status, copy.getStatus());
        }
        verify(copies, never()).flush(); verify(lifecycle, never()).append(any(), any(), any(), any());
    }
    @Test void historyFailurePropagatesSoTransactionalInterceptorRollsBack() {
        found(); doThrow(new IllegalStateException("history failed")).when(lifecycle).append(any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> repair("Bong gáy"));
        // Transaction/database rollback is exercised by the SQL regression/local integration checklist.
    }
    @Test void rejectsUnknownCopyAndMissingPrincipal() {
        assertEquals("COPY_NOT_FOUND", assertThrows(ApiException.class, () -> repair("Bong gáy")).getCode());
        assertEquals("AUTH_REQUIRED", assertThrows(ApiException.class,
                () -> service.repair(10L, new RepairBookCopyRequest("Bong gáy"), null)).getCode());
        verifyNoInteractions(lifecycle);
    }
}
