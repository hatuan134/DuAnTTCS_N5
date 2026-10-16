package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffReservationCancellationServiceTest {
    private BookRepository books;
    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private UserRepository users;
    private LibraryConfigurationService calendar;
    private BookReservationService service;
    private Book book;
    private User staff;
    private BookReservation target;

    @BeforeEach
    void setup() {
        books = mock(BookRepository.class);
        reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class);
        users = mock(UserRepository.class);
        calendar = mock(LibraryConfigurationService.class);
        service = new BookReservationService(books, reservations, users,
                mock(LibraryCardRepository.class), copies, calendar);
        book = new Book(); book.setId(7L); book.setTitle("Mắt biếc");
        staff = user(12L, "Thủ thư An", "LIBRARIAN");
        when(users.findById(12L)).thenReturn(Optional.of(staff));
        target = order(21L, "PENDING", "Bạn đọc Bình");
        when(reservations.findBookIdForCancellation(21L)).thenReturn(Optional.of(7L));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(reservations.findForCancellation(21L)).thenReturn(Optional.of(target));
    }

    private User user(long id, String name, String roleCode) {
        User u = new User(); u.setId(id); u.setFullName(name); u.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); u.setRole(role); return u;
    }

    private BookReservation order(long id, String status, String name) {
        BookReservation r = new BookReservation(book, user(id + 100, name, "READER"), status);
        r.setId(id); r.setReservedAt(OffsetDateTime.now().minusDays(1)); return r;
    }

    private BookCopy readyCopy() {
        BookCopy c = new BookCopy();
        ReflectionTestUtils.setField(c, "id", 101L);
        ReflectionTestUtils.setField(c, "book", book);
        ReflectionTestUtils.setField(c, "barcode", "LIB-101");
        ReflectionTestUtils.setField(c, "status", "HELD");
        target.setStatus("READY_FOR_PICKUP"); target.setBookCopy(c);
        target.setPickupDeadline(OffsetDateTime.now().plusDays(2));
        when(copies.findForStatusChange(101L)).thenReturn(Optional.of(c));
        return c;
    }

    private void expectError(Runnable action, int status, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void pendingCancellationRecordsAuthenticatedActorTimeAndTrimmedReasonAndUpdatesPositions() {
        var before = OffsetDateTime.now().minusSeconds(1);
        var result = service.cancelByStaff(21L, 12L, "  Bạn đọc yêu cầu qua điện thoại  ");
        assertThat(target.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.cancellation().actorId()).isEqualTo(12L);
        assertThat(result.cancellation().actorName()).isEqualTo("Thủ thư An");
        assertThat(result.cancellation().reason()).isEqualTo("Bạn đọc yêu cầu qua điện thoại");
        assertThat(result.cancellation().cancelledAt().toInstant())
                .isBetween(before.toInstant(), OffsetDateTime.now().plusSeconds(1).toInstant());
        assertThat(result.copyOutcome()).isEqualTo("NO_COPY");
        verify(reservations).saveAndFlush(target);
        verifyNoInteractions(copies, calendar);
        var second = order(22L, "PENDING", "Bạn đọc Chi");
        when(books.findById(7L)).thenReturn(Optional.of(book));
        when(reservations.findAllForQueueByBookId(7L)).thenReturn(List.of(target, second));
        var queue = service.getQueueByBookId(7L).items();
        assertThat(queue.get(0).cancellation()).isEqualTo(result.cancellation());
        assertThat(queue.get(0).queuePosition()).isNull();
        assertThat(queue.get(1).queuePosition()).isEqualTo(1L);
    }

    @Test
    void blankWhitespaceAndOversizedReasonAreRejectedWithoutReservationOrCopyAccess() {
        for (String reason : new String[]{null, "", " \t\n ", "\u00a0\t\u00a0", "x".repeat(501)}) {
            expectError(() -> service.cancelByStaff(21L, 12L, reason), 400, "INVALID_CANCELLATION_REASON");
        }
        verifyNoInteractions(books, reservations, copies, calendar);
    }

    @Test
    void fiveHundredCharactersAndShortNonblankReasonAreAllowed() {
        assertThat(service.cancelByStaff(21L, 12L, "x".repeat(500)).cancellation().reason()).hasSize(500);
        target = order(21L, "PENDING", "Bình");
        when(reservations.findForCancellation(21L)).thenReturn(Optional.of(target));
        assertThat(service.cancelByStaff(21L, 12L, "x").cancellation().reason()).isEqualTo("x");
    }

    @Test
    void readyCancellationTransfersExactHeldCopyToFirstPendingAndCreatesFreshDeadline() {
        BookCopy copy = readyCopy();
        var next = order(23L, "PENDING", "Bạn đọc Chi");
        var later = order(24L, "PENDING", "Bạn đọc Dũng");
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.of(next));
        when(calendar.calculateReservationPickupDeadline(any())).thenAnswer(i ->
                ((OffsetDateTime) i.getArgument(0)).plusDays(3));
        var result = service.cancelByStaff(21L, 12L, "Không thể đến nhận");
        assertThat(result.copyOutcome()).isEqualTo("TRANSFERRED");
        assertThat(result.copyId()).isEqualTo(101L); assertThat(result.barcode()).isEqualTo("LIB-101");
        assertThat(result.nextReservationId()).isEqualTo(23L);
        assertThat(result.nextReaderName()).isEqualTo("Bạn đọc Chi");
        assertThat(next.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(next.getBookCopy()).isSameAs(copy); assertThat(copy.getStatus()).isEqualTo("HELD");
        assertThat(target.getBookCopy()).isSameAs(copy); // keep historical allocation
        assertThat(next.getPickupDeadline()).isEqualTo(result.cancellation().cancelledAt().plusDays(3));
        var order = inOrder(books, reservations, copies);
        order.verify(reservations).findBookIdForCancellation(21L);
        order.verify(books).findForReservation(7L);
        order.verify(reservations).findForCancellation(21L);
        order.verify(copies).findForStatusChange(101L);
        order.verify(copies).hasUnreturnedLoan(101L);
        order.verify(reservations).findNextPendingForCancellation(7L);
        order.verify(reservations).saveAndFlush(target);
        order.verify(reservations).saveAndFlush(next);
        when(books.findById(7L)).thenReturn(Optional.of(book));
        when(reservations.findAllForQueueByBookId(7L)).thenReturn(List.of(target, next, later));
        assertThat(service.getQueueByBookId(7L, "PENDING").items().get(0).queuePosition()).isEqualTo(1L);
    }

    @Test
    void readyCancellationWithoutWaiterMakesCopyAvailable() {
        BookCopy copy = readyCopy();
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.empty());
        var result = service.cancelByStaff(21L, 12L, "Bạn đọc không còn nhu cầu");
        assertThat(result.copyOutcome()).isEqualTo("AVAILABLE");
        assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
        assertThat(result.nextReservationId()).isNull();
        assertThat(result.pickupDeadline()).isNull();
        verify(copies).saveAndFlush(copy); verifyNoInteractions(calendar);
    }

    @Test
    void legacyReadyWithoutAllocationCancelsWithoutInventingACopy() {
        target.setStatus("READY_FOR_PICKUP");
        assertThat(service.cancelByStaff(21L, 12L, "Đối chiếu đơn cũ").copyOutcome()).isEqualTo("NO_COPY");
        verifyNoInteractions(copies, calendar);
    }

    @Test
    void loanLinkedReadyReservationCannotBeCancelledByStaffEither() {
        BookCopy copy = readyCopy();
        when(reservations.hasLoanLinkedToReservation(21L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelByStaff(21L, 12L, "Bạn đọc yêu cầu huỷ"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RESERVATION_ALREADY_BORROWED");
                    assertThat(e.getMessage()).contains("sách đã được nhận");
                });

        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(copy.getStatus()).isEqualTo("HELD");
        verify(reservations, never()).findNextPendingForCancellation(anyLong());
        verify(reservations, never()).saveAndFlush(any());
        verify(copies, never()).saveAndFlush(any());
        verify(copies, never()).hasUnreturnedLoan(anyLong());
        verifyNoInteractions(calendar);
    }

    @Test
    void terminalStatesAndRepeatedCancellationDoNotOverwriteAudit() {
        var first = service.cancelByStaff(21L, 12L, "Lý do ban đầu");
        clearInvocations(reservations);
        for (String state : new String[]{"CANCELLED", "EXPIRED"}) {
            target.setStatus(state);
            expectError(() -> service.cancelByStaff(21L, 12L, "Lý do khác"), 409, "RESERVATION_NOT_CANCELLABLE");
            assertThat(target.getCancellationReason()).isEqualTo(first.cancellation().reason());
            assertThat(target.getCancelledAt()).isEqualTo(first.cancellation().cancelledAt());
        }
        target.setStatus("FULFILLED");
        expectError(() -> service.cancelByStaff(21L, 12L, "Lý do khác"), 409, "RESERVATION_ALREADY_BORROWED");
        assertThat(target.getCancellationReason()).isEqualTo(first.cancellation().reason());
        assertThat(target.getCancelledAt()).isEqualTo(first.cancellation().cancelledAt());
        verify(reservations, never()).saveAndFlush(any()); verifyNoInteractions(copies);
    }

    @Test
    void changedCopyOrUnreturnedLoanPreventsCancellationAndRelease() {
        var copy = readyCopy();
        ReflectionTestUtils.setField(copy, "status", "BORROWED");
        expectError(() -> service.cancelByStaff(21L, 12L, "Yêu cầu huỷ"), 409, "RESERVATION_COPY_CONFLICT");
        ReflectionTestUtils.setField(copy, "status", "HELD");
        when(copies.hasUnreturnedLoan(101L)).thenReturn(true);
        expectError(() -> service.cancelByStaff(21L, 12L, "Yêu cầu huỷ"), 409, "RESERVATION_COPY_CONFLICT");
        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(target.getCancelledAt()).isNull();
        verify(reservations, never()).saveAndFlush(any());
        verify(copies, never()).saveAndFlush(any());
    }

    @Test
    void calendarFailureLeavesEveryStateUnchangedBeforeAnyWrite() {
        var copy = readyCopy(); var next = order(23L, "PENDING", "Chi");
        when(reservations.findNextPendingForCancellation(7L)).thenReturn(Optional.of(next));
        when(calendar.calculateReservationPickupDeadline(any())).thenThrow(
                new ApiException(org.springframework.http.HttpStatus.CONFLICT, "INVALID_CALENDAR", "Lịch chưa hợp lệ"));
        expectError(() -> service.cancelByStaff(21L, 12L, "Yêu cầu huỷ"), 409, "INVALID_CALENDAR");
        assertThat(target.getStatus()).isEqualTo("READY_FOR_PICKUP");
        assertThat(next.getStatus()).isEqualTo("PENDING"); assertThat(copy.getStatus()).isEqualTo("HELD");
        assertThat(target.getCancelledAt()).isNull();
        verify(reservations, never()).saveAndFlush(any()); verify(copies, never()).saveAndFlush(any());
    }

    @Test
    void invalidActorIdStatusRoleAndMissingOrderAreRejected() {
        expectError(() -> service.cancelByStaff(21L, null, "Lý do"), 401, "LOGIN_REQUIRED");
        staff.setStatus("LOCKED");
        expectError(() -> service.cancelByStaff(21L, 12L, "Lý do"), 403, "STAFF_ROLE_REQUIRED");
        staff.setStatus("ACTIVE"); staff.getRole().setCode("READER");
        expectError(() -> service.cancelByStaff(21L, 12L, "Lý do"), 403, "STAFF_ROLE_REQUIRED");
        staff.getRole().setCode("LIBRARIAN");
        for (Long id : new Long[]{null, 0L, -1L}) {
            expectError(() -> service.cancelByStaff(id, 12L, "Lý do"), 400, "INVALID_RESERVATION_ID");
        }
        when(reservations.findBookIdForCancellation(21L)).thenReturn(Optional.empty());
        expectError(() -> service.cancelByStaff(21L, 12L, "Lý do"), 404, "RESERVATION_NOT_FOUND");
        verifyNoInteractions(copies, calendar);
    }
}
