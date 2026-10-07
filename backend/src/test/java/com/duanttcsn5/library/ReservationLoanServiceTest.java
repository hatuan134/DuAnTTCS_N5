package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.dto.loan.LoanDatePreviewResponse;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import java.time.ZoneId;
import java.time.Clock;
import com.duanttcsn5.library.exception.ReservationPickupExpiredException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationLoanServiceTest {
    private BookRepository books;
    private BookReservationRepository reservations;
    private BookCopyRepository copies;
    private LibraryCardRepository cards;
    private UserRepository users;
    private LoanRepository loans;
    private LoanService service;
    private LibraryConfigurationService configuration;
    private BookReservation reservation;
    private BookCopy copy;
    private LibraryCard card;
    private User actor;
    private Clock clock;
    private static final OffsetDateTime CONFIRMED = OffsetDateTime.parse("2026-10-07T17:00:00+07:00");

    @BeforeEach
    void setup() {
        books = mock(BookRepository.class); reservations = mock(BookReservationRepository.class);
        copies = mock(BookCopyRepository.class); cards = mock(LibraryCardRepository.class);
        users = mock(UserRepository.class); loans = mock(LoanRepository.class);
        configuration = mock(LibraryConfigurationService.class);
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(clock.instant()).thenReturn(CONFIRMED.toInstant());
        service = new LoanService(books, reservations, copies, cards, users, loans, configuration, clock);
        actor = new User(); actor.setId(3L); actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); actor.setRole(role);
        when(users.findById(3L)).thenReturn(Optional.of(actor));
        Book book = new Book(); book.setId(7L); book.setTitle("Mắt biếc");
        User reader = new User(); reader.setId(12L); reader.setFullName("Nguyễn Văn An");
        copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(31L); when(copy.getBook()).thenReturn(book);
        when(copy.getBarcode()).thenReturn("LIB-031"); when(copy.getStatus()).thenReturn("HELD");
        reservation = new BookReservation(book, reader, "READY_FOR_PICKUP");
        reservation.setId(21L); reservation.setBookCopy(copy);
        reservation.setReservedAt(CONFIRMED.minusDays(10));
        reservation.setPickupDeadline(CONFIRMED.plusDays(3));
        card = new LibraryCard(); card.setCardNumber("TV-0012"); card.setUser(reader);
        CardType type = new CardType(); type.setName("Thẻ sinh viên"); type.setLoanDays(14); card.setCardType(type);
        when(configuration.calculateLoanDates(any(), eq(14), eq("Thẻ sinh viên"))).thenAnswer(call -> {
            OffsetDateTime borrowedAt = call.getArgument(0);
            var day = borrowedAt.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
            var due = day.plusDays(14);
            return new LoanDatePreviewResponse(day, "Thẻ sinh viên", 14, due, due,
                    due.atTime(17, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(), false, List.of());
        });
        when(reservations.findBookIdForCancellation(21L)).thenReturn(Optional.of(7L));
        when(books.findForReservation(7L)).thenReturn(Optional.of(book));
        when(reservations.findForCancellation(21L)).thenReturn(Optional.of(reservation));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        when(copies.findForStatusChange(31L)).thenReturn(Optional.of(copy));
        when(loans.insert(eq(21L), eq(12L), eq(3L), anyString(), any())).thenReturn(81L);
    }

    private void rejected(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void validCardCreatesExactReaderHeldCopyAndCalculatedDatesBeforePickupDeadline() {
        var result = service.createFromReservation(21L, 3L, "  TV-0012  ");
        assertThat(result.id()).isEqualTo(81L); assertThat(result.reservationId()).isEqualTo(21L);
        assertThat(result.readerId()).isEqualTo(12L); assertThat(result.readerName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.cardNumber()).isEqualTo("TV-0012"); assertThat(result.copyId()).isEqualTo(31L);
        assertThat(result.barcode()).isEqualTo("LIB-031"); assertThat(result.bookId()).isEqualTo(7L);
        assertThat(result.loanNumber()).matches("PM-[0-9A-F-]{36}");
        var ordered = inOrder(books, reservations, copies, loans);
        ordered.verify(books).findForReservation(7L);
        ordered.verify(reservations).findForCancellation(21L);
        ordered.verify(copies).findForStatusChange(31L);
        ordered.verify(loans).insert(21L, 12L, 3L, result.loanNumber(), result.borrowedAt());
        ordered.verify(loans).insertItem(81L, 31L, result.borrowedAt(), result.dates().dueAt());
        assertThat(result.dates().borrowDate()).isEqualTo(result.borrowedAt().toLocalDate());
        assertThat(result.dates().dueDate()).isEqualTo(result.dates().borrowDate().plusDays(14));
        ordered.verify(reservations).saveAndFlush(reservation);
        assertThat(reservation.getStatus()).isEqualTo("FULFILLED");
        verify(copies, never()).save(any());
    }

    @Test void wrongCardCannotCreateAnyLoan() {
        rejected("RESERVATION_CARD_MISMATCH", () -> service.createFromReservation(21L, 3L, "TV-OTHER"));
        verify(copies, never()).findForStatusChange(any());
    }
    @Test void emptyWhitespaceNullAndOversizeCardsFailBeforeLocking() {
        for (String input : new String[]{null, "", "   ", "\u00A0", "x".repeat(101)})
            rejected("INVALID_CARD_NUMBER", () -> service.createFromReservation(21L, 3L, input));
        verifyNoInteractions(books, reservations, copies, cards);
    }
    @Test void repeatedConversionIsRejectedEvenWhenReservationIsStillReady() {
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-OLD"));
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void legacyLinkedLoanAlsoBlocksConversion() {
        when(reservations.hasLoanLinkedToReservation(21L)).thenReturn(true);
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void fulfilledReservationCannotBeConvertedAgain() {
        reservation.setStatus("FULFILLED");
        rejected("RESERVATION_ALREADY_CONVERTED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void onlyReadyReservationsCanCreateLoans() {
        for (String status : new String[]{"PENDING", "CANCELLED"}) {
            reservation.setStatus(status);
            rejected("RESERVATION_NOT_READY", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        }
    }
    @Test void missingCardAndUnallocatedCopyAreRejected() {
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.empty());
        rejected("LIBRARY_CARD_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(cards.findByUserIdWithDetails(12L)).thenReturn(Optional.of(card));
        reservation.setBookCopy(null);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void wrongBookNonHeldOrAlreadyBorrowedCopyIsRejected() {
        when(copy.getStatus()).thenReturn("AVAILABLE");
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(copy.getStatus()).thenReturn("HELD"); when(copies.hasUnreturnedLoan(31L)).thenReturn(true);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        when(copies.hasUnreturnedLoan(31L)).thenReturn(false);
        Book other = new Book(); other.setId(8L); when(copy.getBook()).thenReturn(other);
        rejected("RESERVATION_COPY_CONFLICT", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void readerInactiveStaffAndUnauthenticatedCallAreRejected() {
        actor.getRole().setCode("READER");
        rejected("STAFF_ROLE_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        actor.getRole().setCode("LIBRARIAN"); actor.setStatus("LOCKED");
        rejected("STAFF_ROLE_REQUIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        rejected("LOGIN_REQUIRED", () -> service.createFromReservation(21L, null, "TV-0012"));
    }
    @Test void invalidOrMissingReservationUsesExistingErrors() {
        rejected("INVALID_RESERVATION_ID", () -> service.createFromReservation(0L, 3L, "TV-0012"));
        rejected("RESERVATION_NOT_FOUND", () -> service.createFromReservation(999L, 3L, "TV-0012"));
    }
    @Test void contextShowsCardAndPersistedConversionAfterReload() {
        when(reservations.findForLoanContext(21L)).thenReturn(Optional.of(reservation));
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-OLD"));
        var context = service.pickupContext(21L);
        assertThat(context.cardNumber()).isEqualTo("TV-0012");
        assertThat(context.converted()).isTrue(); assertThat(context.loanNumber()).isEqualTo("PM-OLD");
    }

    @Test void missingLoanDaysBlocksCreationBeforeAnyWrite() {
        card.getCardType().setLoanDays(0);
        rejected("LOAN_POLICY_NOT_CONFIGURED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void incompleteCalendarBlocksCreationBeforeAnyWrite() {
        when(configuration.calculateLoanDates(any(), anyInt(), anyString())).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE", "Chưa cấu hình lịch."));
        rejected("WEEKLY_SCHEDULE_INCOMPLETE", () -> service.createFromReservation(21L, 3L, "TV-0012"));
    }
    @Test void stalePreviewIsRejectedBeforeSavingAndCanBeConfirmedAfterRefresh() {
        when(reservations.findForLoanContext(21L)).thenReturn(Optional.of(reservation));
        var preview = service.pickupContext(21L).dates();
        rejected("LOAN_DATES_CHANGED", () -> service.createFromReservation(21L, 3L, "TV-0012",
                preview.borrowDate().minusDays(1), preview.dueAt(), preview.loanDays()));
        rejected("LOAN_DATES_CHANGED", () -> service.createFromReservation(21L, 3L, "TV-0012",
                preview.borrowDate(), preview.dueAt().minusDays(1), preview.loanDays()));
        rejected("LOAN_DATES_CHANGED", () -> service.createFromReservation(21L, 3L, "TV-0012",
                preview.borrowDate(), preview.dueAt(), 7));
        var result = service.createFromReservation(21L, 3L, "TV-0012",
                preview.borrowDate(), preview.dueAt(), preview.loanDays());
        assertThat(result.dates().dueAt()).isEqualTo(preview.dueAt());
    }
    @Test void contextShowsPreviewAndConfigurationErrorsWithoutBreakingTheDetailPage() {
        when(reservations.findForLoanContext(21L)).thenReturn(Optional.of(reservation));
        var preview = service.pickupContext(21L);
        assertThat(preview.dates().loanDays()).isEqualTo(14);
        assertThat(preview.dateError()).isNull();
        card.setCardType(null);
        var invalid = service.pickupContext(21L);
        assertThat(invalid.cardNumber()).isEqualTo("TV-0012");
        assertThat(invalid.dates()).isNull();
        assertThat(invalid.dateError()).contains("số ngày mượn");
    }

    @Test void failedHeaderDoesNotChangeReservationOrInsertItem() {
        when(loans.insert(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("header failed"));
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        verify(loans, never()).insertItem(any(), any(), any(), any());
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void failedItemDoesNotStartReservationTransition() {
        doThrow(new IllegalStateException("item failed")).when(loans).insertItem(any(), any(), any(), any());
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test void failedReservationFlushPropagatesSoTheOwningTransactionCanRollBack() {
        when(reservations.saveAndFlush(reservation)).thenThrow(new IllegalStateException("status failed"));
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOf(IllegalStateException.class).hasMessage("status failed");
        var ordered = inOrder(loans, reservations);
        ordered.verify(loans).insert(eq(21L), eq(12L), eq(3L), anyString(), any());
        ordered.verify(loans).insertItem(eq(81L), eq(31L), any(), any());
        ordered.verify(reservations).saveAndFlush(reservation);
        // Actual rollback is checked with PostgreSQL, not inferred from Mockito.
    }

    @Test void fulfilledContextWorksAfterLostResponseWithoutRecalculatingPolicy() {
        reservation.setStatus("FULFILLED");
        card.setCardType(null);
        when(reservations.findForLoanContext(21L)).thenReturn(Optional.of(reservation));
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-SAVED"));
        var context = service.pickupContext(21L);
        assertThat(context.converted()).isTrue();
        assertThat(context.loanNumber()).isEqualTo("PM-SAVED");
        assertThat(context.dates()).isNull();
        verifyNoInteractions(configuration);
    }

    private void expiredWithoutLoan() {
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOfSatisfying(ReservationPickupExpiredException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RESERVATION_PICKUP_EXPIRED");
                    assertThat(error.getMessage()).contains("quá hạn nhận", "đặt giữ lại");
                });
        assertThat(reservation.getStatus()).isEqualTo("EXPIRED");
        verify(reservations).saveAndFlush(reservation);
        verify(copy).releaseReservationHold();
        verify(copies).saveAndFlush(copy);
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
        verify(reservations, never()).findNextPendingForCancellation(any());
        verifyNoInteractions(configuration);
    }

    @Test void oneMicrosecondBeforeDeadlineIsAllowed() {
        reservation.setPickupDeadline(CONFIRMED.plusNanos(1000));
        assertThat(service.createFromReservation(21L, 3L, "TV-0012").id()).isEqualTo(81L);
        verify(copy, never()).releaseReservationHold();
    }
    @Test void exactDeadlineIsAllowed() {
        reservation.setPickupDeadline(CONFIRMED);
        assertThat(service.createFromReservation(21L, 3L, "TV-0012").borrowedAt()).isEqualTo(CONFIRMED);
    }
    @Test void sameInstantInAnotherOffsetIsAllowed() {
        reservation.setPickupDeadline(CONFIRMED.withOffsetSameInstant(java.time.ZoneOffset.UTC));
        assertThat(service.createFromReservation(21L, 3L, "TV-0012").id()).isEqualTo(81L);
    }
    @Test void oneMicrosecondAfterDeadlineExpiresAndReleasesWithoutAnyLoan() {
        reservation.setPickupDeadline(CONFIRMED.minusNanos(1000));
        expiredWithoutLoan();
    }
    @Test void samePickupDayBeforeClosingTimeIsAllowed() {
        reservation.setPickupDeadline(CONFIRMED);
        when(clock.instant()).thenReturn(CONFIRMED.minusMinutes(30).toInstant());
        assertThat(service.createFromReservation(21L, 3L, "TV-0012").id()).isEqualTo(81L);
    }
    @Test void samePickupDayAfterClosingTimeIsExpired() {
        reservation.setPickupDeadline(CONFIRMED);
        when(clock.instant()).thenReturn(CONFIRMED.plusMinutes(1).toInstant());
        expiredWithoutLoan();
    }
    @Test void timeIsSampledAfterWaitingForTheCopyLock() {
        reservation.setPickupDeadline(CONFIRMED.plusSeconds(1));
        when(copies.findForStatusChange(31L)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(CONFIRMED.plusSeconds(2).toInstant());
            return Optional.of(copy);
        });
        expiredWithoutLoan();
    }
    @Test void readOnlyContextFlagsExpiryWithoutChangingData() {
        reservation.setPickupDeadline(CONFIRMED.minusSeconds(1));
        when(reservations.findForLoanContext(21L)).thenReturn(Optional.of(reservation));
        var result = service.pickupContext(21L);
        assertThat(result.expired()).isTrue();
        assertThat(result.checkedAt()).isEqualTo(CONFIRMED);
        assertThat(result.status()).isEqualTo("READY_FOR_PICKUP");
        assertThat(result.reservation().id()).isEqualTo(21L);
        assertThat(result.pickupMessage()).contains("đặt giữ lại");
        assertThat(result.dates()).isNull();
        assertThat(reservation.getStatus()).isEqualTo("READY_FOR_PICKUP");
        verify(reservations, never()).saveAndFlush(any());
        verify(copy, never()).releaseReservationHold();
    }
    @Test void explicitOpenCheckPersistsExpiryAndReturnsAvailableCopyForReload() {
        reservation.setPickupDeadline(CONFIRMED.minusSeconds(1));
        doAnswer(call -> { when(copy.getStatus()).thenReturn("AVAILABLE"); return null; })
                .when(copy).releaseReservationHold();
        var result = service.checkPickup(21L, 3L);
        assertThat(result.expired()).isTrue();
        assertThat(result.status()).isEqualTo("EXPIRED");
        assertThat(result.copyStatus()).isEqualTo("AVAILABLE");
        assertThat(result.reservation().status()).isEqualTo("EXPIRED");
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(reservations, never()).findNextPendingForCancellation(any());
        var reloaded = service.checkPickup(21L, 3L);
        assertThat(reloaded.expired()).isTrue();
        verify(copy, times(1)).releaseReservationHold();
        verify(copies, times(1)).saveAndFlush(copy);
    }
    @Test void alreadyExpiredReturnsTheSameDomainErrorWithoutReleasingTwice() {
        reservation.setStatus("EXPIRED");
        rejected("RESERVATION_PICKUP_EXPIRED", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        verify(copy, never()).releaseReservationHold();
        verify(copies, never()).saveAndFlush(any());
    }
    @Test void missingDeadlineBlocksConversionAndReturnsAnExplanation() {
        reservation.setPickupDeadline(null);
        rejected("RESERVATION_PICKUP_DEADLINE_MISSING", () -> service.createFromReservation(21L, 3L, "TV-0012"));
        var result = service.checkPickup(21L, 3L);
        assertThat(result.expired()).isFalse();
        assertThat(result.pickupMessage()).contains("chưa có hạn nhận");
        assertThat(result.dates()).isNull();
    }
    @Test void convertedOrderWithPastDeadlineNeverReleasesItsBorrowedCopy() {
        reservation.setPickupDeadline(CONFIRMED.minusDays(1));
        when(loans.findNumberByReservation(21L)).thenReturn(Optional.of("PM-OLD"));
        var result = service.checkPickup(21L, 3L);
        assertThat(result.converted()).isTrue();
        assertThat(result.expired()).isFalse();
        verify(copy, never()).releaseReservationHold();
        verify(copies, never()).findForStatusChange(any());
    }
    @Test void failedReleasePropagatesToRollBackTheEntireExpiryTransaction() {
        reservation.setPickupDeadline(CONFIRMED.minusSeconds(1));
        when(copies.saveAndFlush(copy)).thenThrow(new IllegalStateException("release failed"));
        assertThatThrownBy(() -> service.createFromReservation(21L, 3L, "TV-0012"))
                .isInstanceOf(IllegalStateException.class).hasMessage("release failed");
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        // PostgreSQL test verifies persisted rollback; Mockito only verifies the failure is propagated.
    }

    @Test void loanDetailReadsSavedItemsAndOriginalCreatorWithoutRecalculatingPolicy() {
        var due = CONFIRMED.plusDays(14);
        var header = new LoanDetailResponse(81L, "PM-SAVED", 21L, 12L, "Nguyễn Văn An",
                9L, "Thủ thư lập phiếu", CONFIRMED, List.of());
        var item = new LoanDetailResponse.Item(91L, 31L, "LIB-031", 7L, "Mắt biếc", CONFIRMED, due);
        when(loans.findHeaderForStaff(81L)).thenReturn(Optional.of(header));
        when(loans.findItemsForStaff(81L)).thenReturn(List.of(item));
        card.getCardType().setLoanDays(1);
        var result = service.loanDetail(81L, 3L);
        assertThat(result.loanNumber()).isEqualTo("PM-SAVED");
        assertThat(result.createdById()).isEqualTo(9L);
        assertThat(result.createdByName()).isEqualTo("Thủ thư lập phiếu");
        assertThat(result.borrowedAt()).isEqualTo(CONFIRMED);
        assertThat(result.items()).containsExactly(item);
        assertThat(result.items().get(0).dueAt()).isEqualTo(due);
        assertThat(service.loanDetail(81L, 3L)).isEqualTo(result);
        verifyNoInteractions(configuration, books, reservations, copies, cards);
        verify(loans, never()).insert(any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }
    @Test void listLoansKeepsRepositoryOrderAndAcceptsAllExistingStaffRoles() {
        var row = new LoanSummaryResponse(81L, "PM-SAVED", 12L, "Bạn đọc", 9L, "Người lập", CONFIRMED, 1);
        when(loans.findAllForStaff()).thenReturn(List.of(row));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            actor.getRole().setCode(role);
            assertThat(service.listLoans(3L)).containsExactly(row);
        }
        verifyNoInteractions(configuration, reservations, copies, cards, books);
    }
    @Test void invalidAndMissingLoanIdsReturnDomainErrorsWithoutReadingItems() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            rejected("INVALID_LOAN_ID", () -> service.loanDetail(id, 3L));
        }
        rejected("LOAN_NOT_FOUND", () -> service.loanDetail(999L, 3L));
        verify(loans, never()).findItemsForStaff(any());
    }
    @Test void loanReadServiceRejectsUnauthenticatedReaderAndInactiveStaff() {
        rejected("LOGIN_REQUIRED", () -> service.loanDetail(81L, null));
        rejected("LOGIN_REQUIRED", () -> service.listLoans(null));
        actor.getRole().setCode("READER");
        rejected("STAFF_ROLE_REQUIRED", () -> service.loanDetail(81L, 3L));
        rejected("STAFF_ROLE_REQUIRED", () -> service.listLoans(3L));
        actor.getRole().setCode("LIBRARIAN"); actor.setStatus("LOCKED");
        rejected("STAFF_ROLE_REQUIRED", () -> service.loanDetail(81L, 3L));
        verify(loans, never()).findHeaderForStaff(any());
        verify(loans, never()).findAllForStaff();
    }
    @Test void legacyLoanWithMultipleItemsAndMissingDueDateDoesNotInventData() {
        var header = new LoanDetailResponse(81L, "PM-LEGACY", null, 12L, "Bạn đọc", 3L, "Thủ thư", CONFIRMED, List.of());
        var first = new LoanDetailResponse.Item(91L, 31L, "COPY-A", 7L, "Sách A", CONFIRMED, null);
        var second = new LoanDetailResponse.Item(92L, 32L, "COPY-B", 8L, "Sách B", CONFIRMED.plusDays(1), CONFIRMED.plusDays(15));
        when(loans.findHeaderForStaff(81L)).thenReturn(Optional.of(header));
        when(loans.findItemsForStaff(81L)).thenReturn(List.of(first, second));
        var result = service.loanDetail(81L, 3L);
        assertThat(result.reservationId()).isNull();
        assertThat(result.items()).containsExactly(first, second);
        assertThat(result.items().get(0).dueAt()).isNull();
        verifyNoInteractions(configuration);
    }
}
