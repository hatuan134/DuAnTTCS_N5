package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnConfirmationServiceTest {
    LoanRepository loans = mock(LoanRepository.class);
    UserRepository users = mock(UserRepository.class);
    User staff;
    LoanService service;
    final OffsetDateTime returnedAt = OffsetDateTime.parse("2026-10-09T01:00:00+07:00");
    final ConfirmReturnRequest request = new ConfirmReturnRequest(" LIB-001 ", 9L);

    @BeforeEach void setup() {
        service = new LoanService(null, null, null, null, users, loans, null,
                Clock.fixed(returnedAt.toInstant(), ZoneOffset.UTC));
        staff = new User(); staff.setId(12L); staff.setFullName("Thủ thư An"); staff.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); staff.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(staff));
    }
    void open() {
        when(loans.lockTitleForReturn(9L, "LIB-001")).thenReturn(Optional.of(10L));
        when(loans.lockReturnCandidate(9L, "LIB-001")).thenReturn(Optional.of(
                new LoanRepository.ReturnCandidate(9L, 8L, 4L,
                        returnedAt.minusDays(7), null)));
        when(loans.lockCopyForReturn(4L)).thenReturn(Optional.of("BORROWED"));
    }
    ConfirmReturnResponse result(String loanStatus, String copyStatus) {
        return new ConfirmReturnResponse("Nhận trả sách thành công.", 4L, "LIB-001", "Mắt biếc",
                8L, "PM-008", 9L, "RETURNED", loanStatus, copyStatus, returnedAt, 12L, "Thủ thư An");
    }
    void saved(String loanStatus) {
        open(); when(loans.markReturned(9L, 4L, returnedAt, 12L)).thenReturn(1);
        when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(result(loanStatus, "AVAILABLE")));
    }
    void code(Runnable action, String expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }
    @Test void confirmsSelectedItemWithVietnamTimestampAndReceiverAfterLocks() {
        saved("RETURNED"); var result = service.confirmReturn(request, 12L);
        assertThat(result.itemStatus()).isEqualTo("RETURNED");
        assertThat(result.loanStatus()).isEqualTo("RETURNED");
        assertThat(result.copyStatus()).isEqualTo("AVAILABLE");
        assertThat(result.returnedAt()).isEqualTo(returnedAt);
        assertThat(result.returnedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(result.returnedById()).isEqualTo(12L);
        var order = inOrder(loans);
        order.verify(loans).lockTitleForReturn(9L, "LIB-001");
        order.verify(loans).lockReturnCandidate(9L, "LIB-001");
        order.verify(loans).lockPendingQueueForReturn(10L);
        order.verify(loans).lockCopyForReturn(4L);
        order.verify(loans).findEligiblePendingForReturn(10L, returnedAt.toLocalDate());
        order.verify(loans).markReturned(9L, 4L, returnedAt, 12L);
        order.verify(loans).findReturnConfirmation(9L);
    }
    @Test void allExistingStaffRolesMayConfirm() {
        saved("RETURNED");
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            staff.getRole().setCode(role);
            assertThat(service.confirmReturn(request, 12L).returnedById()).isEqualTo(12L);
        }
    }
    @Test void partiallyReturnedLoanKeepsItsOtherBooksOpen() {
        saved("BORROWED"); assertThat(service.confirmReturn(request, 12L).loanStatus()).isEqualTo("BORROWED");
        verify(loans).markReturned(9L, 4L, returnedAt, 12L);
    }
    @Test void validationNeverWrites() {
        for (String barcode : new String[]{null, "", "  ", "X".repeat(101)}) {
            code(() -> service.confirmReturn(new ConfirmReturnRequest(barcode, 9L), 12L), "INVALID_BARCODE");
        }
        code(() -> service.confirmReturn(null, 12L), "INVALID_BARCODE");
        for (Long id : new Long[]{null, 0L, -1L}) {
            code(() -> service.confirmReturn(new ConfirmReturnRequest("LIB-001", id), 12L), "INVALID_LOAN_ITEM_ID");
        }
        verifyNoInteractions(loans);
    }
    @Test void unauthorizedReaderAndInactiveStaffNeverReadOrWriteTheLoan() {
        code(() -> service.confirmReturn(request, null), "LOGIN_REQUIRED");
        code(() -> service.confirmReturn(request, 99L), "UNAUTHORIZED");
        staff.getRole().setCode("READER"); code(() -> service.confirmReturn(request, 12L), "STAFF_ROLE_REQUIRED");
        staff.getRole().setCode("LIBRARIAN"); staff.setStatus("DISABLED");
        code(() -> service.confirmReturn(request, 12L), "STAFF_ROLE_REQUIRED"); verifyNoInteractions(loans);
    }
    @Test void staleItemOrDifferentBarcodeCannotReturnAnotherLoan() {
        when(loans.lockReturnCandidate(9L, "LIB-001")).thenReturn(Optional.empty());
        code(() -> service.confirmReturn(request, 12L), "RETURN_PREVIEW_CHANGED");
        verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }
    @Test void returnedItemIsRejectedBeforeTouchingCopyOrHistory() {
        when(loans.lockTitleForReturn(9L, "LIB-001")).thenReturn(Optional.of(10L));
        when(loans.lockReturnCandidate(9L, "LIB-001")).thenReturn(Optional.of(
                new LoanRepository.ReturnCandidate(9L, 8L, 4L, returnedAt.minusDays(7), returnedAt.minusDays(1))));
        code(() -> service.confirmReturn(request, 12L), "LOAN_ALREADY_RETURNED");
        verify(loans, never()).lockCopyForReturn(anyLong());
    }
    @Test void mismatchedCopyStateNeverUpdates() {
        open(); when(loans.lockCopyForReturn(4L)).thenReturn(Optional.of("REPAIR"));
        code(() -> service.confirmReturn(request, 12L), "RETURN_COPY_STATUS_MISMATCH");
        verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }
    @Test void futureBorrowDateIsRejected() {
        open(); when(loans.lockReturnCandidate(9L, "LIB-001")).thenReturn(Optional.of(
                new LoanRepository.ReturnCandidate(9L, 8L, 4L, returnedAt.plusDays(1), null)));
        code(() -> service.confirmReturn(request, 12L), "RETURN_BEFORE_BORROWED_AT");
        verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }
    @Test void zeroRowsAndDatabaseFailureThrowInsteadOfReturningSuccess() {
        open(); code(() -> service.confirmReturn(request, 12L), "RETURN_UPDATE_FAILED");
        when(loans.markReturned(9L, 4L, returnedAt, 12L)).thenThrow(new DataIntegrityViolationException("simulated"));
        code(() -> service.confirmReturn(request, 12L), "RETURN_SAVE_FAILED");
    }
    @Test void missingResultOrFailedCopyTransitionThrowsForTransactionRollback() {
        saved("RETURNED"); when(loans.findReturnConfirmation(9L)).thenReturn(Optional.empty());
        code(() -> service.confirmReturn(request, 12L), "RETURN_SAVE_FAILED");
        when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(result("RETURNED", "BORROWED")));
        code(() -> service.confirmReturn(request, 12L), "RETURN_SAVE_FAILED");
    }
}
