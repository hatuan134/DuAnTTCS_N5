package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ConfirmReturnRequest;
import com.duanttcsn5.library.dto.loan.ConfirmReturnResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnQueueAllocationServiceTest {
    final LoanRepository loans = mock(LoanRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LibraryConfigurationService calendar = mock(LibraryConfigurationService.class);
    final OffsetDateTime returnedAt = OffsetDateTime.parse("2026-10-09T01:00:00+07:00");
    final OffsetDateTime deadline = OffsetDateTime.parse("2026-10-13T17:00:00+07:00");
    final ConfirmReturnRequest request = new ConfirmReturnRequest(" LIB-001 ", 9L);
    final LoanRepository.ReturnReservation waiter = new LoanRepository.ReturnReservation(
            30L, "Bạn đọc Bình", returnedAt.minusDays(2));
    LoanService service;

    @BeforeEach void setup() {
        service = new LoanService(null, null, null, null, users, loans, calendar,
                Clock.fixed(returnedAt.toInstant(), ZoneOffset.UTC));
        var staff = new User(); staff.setId(12L); staff.setStatus("ACTIVE");
        var role = new Role(); role.setCode("LIBRARIAN"); staff.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(staff));
        when(loans.lockTitleForReturn(9L, "LIB-001")).thenReturn(Optional.of(10L));
        when(loans.lockReturnCandidate(9L, "LIB-001")).thenReturn(Optional.of(
                new LoanRepository.ReturnCandidate(9L, 8L, 4L, returnedAt.minusDays(7), null)));
        when(loans.lockCopyForReturn(4L)).thenReturn(Optional.of("BORROWED"));
    }

    ConfirmReturnResponse result(String copyStatus, Long reservationId, OffsetDateTime startedAt) {
        return new ConfirmReturnResponse("Nhận trả sách thành công.", 4L, "LIB-001", "Mắt biếc",
                8L, "PM-008", 9L, "RETURNED", "RETURNED", copyStatus, returnedAt, 12L, "Thủ thư An",
                reservationId, reservationId == null ? null : "Bạn đọc Bình", startedAt,
                reservationId == null ? null : deadline);
    }

    void queued() {
        when(loans.findEligiblePendingForReturn(10L, returnedAt.toLocalDate())).thenReturn(Optional.of(waiter));
        when(calendar.calculateReservationPickupDeadline(returnedAt)).thenReturn(deadline);
        when(loans.allocateReturnedCopy(30L, 10L, 4L, returnedAt, deadline)).thenReturn(1);
        when(loans.markReturned(9L, 4L, returnedAt, 12L)).thenReturn(1);
        when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(result("HELD", 30L, returnedAt)));
    }

    void code(String expected) {
        assertThatThrownBy(() -> service.confirmReturn(request, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }

    @Test void emptyOrEntirelyIneligibleQueueReleasesCopyWithoutCalculatingDeadline() {
        when(loans.markReturned(9L, 4L, returnedAt, 12L)).thenReturn(1);
        when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(result("AVAILABLE", null, null)));
        var response = service.confirmReturn(request, 12L);
        assertThat(response.copyStatus()).isEqualTo("AVAILABLE");
        assertThat(response.nextReservationId()).isNull();
        verifyNoInteractions(calendar);
        verify(loans, never()).allocateReturnedCopy(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test void allocatesExactlyOneWaiterBeforeTriggerAndKeepsOriginalQueueTimestamp() {
        queued(); var response = service.confirmReturn(request, 12L);
        assertThat(response.copyStatus()).isEqualTo("HELD");
        assertThat(response.nextReservationId()).isEqualTo(30L);
        assertThat(response.holdStartedAt()).isEqualTo(returnedAt);
        assertThat(response.pickupDeadline()).isEqualTo(deadline);
        assertThat(waiter.reservedAt()).isEqualTo(returnedAt.minusDays(2));
        var order = inOrder(loans, calendar);
        order.verify(loans).lockTitleForReturn(9L, "LIB-001");
        order.verify(loans).lockReturnCandidate(9L, "LIB-001");
        order.verify(loans).lockPendingQueueForReturn(10L);
        order.verify(loans).lockCopyForReturn(4L);
        order.verify(loans).findEligiblePendingForReturn(10L, returnedAt.toLocalDate());
        order.verify(calendar).calculateReservationPickupDeadline(returnedAt);
        order.verify(loans).allocateReturnedCopy(30L, 10L, 4L, returnedAt, deadline);
        order.verify(loans).markReturned(9L, 4L, returnedAt, 12L);
        order.verify(loans).findReturnConfirmation(9L);
        verify(loans, times(1)).allocateReturnedCopy(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test void postgresUtcTimestampRepresentsTheSameHoldStartAsVietnamOffset() {
        queued(); when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(
                result("HELD", 30L, returnedAt.withOffsetSameInstant(ZoneOffset.UTC))));
        assertThat(service.confirmReturn(request, 12L).copyStatus()).isEqualTo("HELD");
    }

    @Test void invalidCalendarFailsBeforeAllocatingOrMarkingReturn() {
        queued(); when(calendar.calculateReservationPickupDeadline(returnedAt)).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE", "Lịch chưa đủ 7 ngày."));
        code("WEEKLY_SCHEDULE_INCOMPLETE");
        verify(loans, never()).allocateReturnedCopy(anyLong(), anyLong(), anyLong(), any(), any());
        verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }

    @Test void missingOrPastDeadlineNeverWrites() {
        queued();
        for (OffsetDateTime invalid : new OffsetDateTime[]{null, returnedAt, returnedAt.minusSeconds(1)}) {
            when(calendar.calculateReservationPickupDeadline(returnedAt)).thenReturn(invalid);
            code("INVALID_PICKUP_DEADLINE");
        }
        verify(loans, never()).allocateReturnedCopy(anyLong(), anyLong(), anyLong(), any(), any());
        verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }

    @Test void futureQueueTimestampCannotBeOverwrittenToForceAnAllocation() {
        queued(); when(loans.findEligiblePendingForReturn(10L, returnedAt.toLocalDate())).thenReturn(Optional.of(
                new LoanRepository.ReturnReservation(30L, "Bạn đọc Bình", returnedAt.plusSeconds(1))));
        code("INVALID_PICKUP_DEADLINE");
        verify(loans, never()).allocateReturnedCopy(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test void lostPendingRowFailsBeforeReturningCopy() {
        queued(); when(loans.allocateReturnedCopy(30L, 10L, 4L, returnedAt, deadline)).thenReturn(0);
        code("RETURN_QUEUE_CHANGED"); verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }

    @Test void duplicateAllocationDatabaseConstraintIsReportedAsAtomicSaveFailure() {
        queued(); when(loans.allocateReturnedCopy(30L, 10L, 4L, returnedAt, deadline))
                .thenThrow(new DataIntegrityViolationException("ux_reservations_ready_copy"));
        code("RETURN_SAVE_FAILED"); verify(loans, never()).markReturned(anyLong(), anyLong(), any(), anyLong());
    }

    @Test void incorrectFinalCopyOrQueueOwnershipThrowsForTransactionRollback() {
        queued();
        for (ConfirmReturnResponse invalid : new ConfirmReturnResponse[]{
                result("AVAILABLE", 30L, returnedAt), result("HELD", 99L, returnedAt),
                result("HELD", 30L, null), result("HELD", 30L, returnedAt.minusSeconds(1))}) {
            when(loans.findReturnConfirmation(9L)).thenReturn(Optional.of(invalid)); code("RETURN_SAVE_FAILED");
        }
    }

    @Test void finalReturnFailurePropagatesAfterAllocationSoTheTransactionRollsBackBoth() {
        queued(); when(loans.markReturned(9L, 4L, returnedAt, 12L))
                .thenThrow(new DataIntegrityViolationException("simulated return failure"));
        code("RETURN_SAVE_FAILED");
        verify(loans).allocateReturnedCopy(30L, 10L, 4L, returnedAt, deadline);
        verify(loans, never()).findReturnConfirmation(anyLong());
    }
}
