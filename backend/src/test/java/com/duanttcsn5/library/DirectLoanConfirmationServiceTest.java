package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectLoanConfirmationServiceTest {
    private BookRepository books;
    private BookCopyRepository copies;
    private BookReservationRepository reservations;
    private LibraryCardRepository cards;
    private UserRepository users;
    private LoanRepository loans;
    private LoanService service;
    private LibraryCard card;
    private final UUID key = UUID.randomUUID();
    private final OffsetDateTime at = OffsetDateTime.parse("2026-10-08T09:00:00+07:00");

    private User user(Long id, String code) {
        var user = new User(); user.setId(id); user.setStatus("ACTIVE"); user.setFullName(code);
        var role = new Role(); role.setCode(code); user.setRole(role); return user;
    }

    @BeforeEach void setup() {
        books = mock(BookRepository.class); copies = mock(BookCopyRepository.class);
        reservations = mock(BookReservationRepository.class); cards = mock(LibraryCardRepository.class);
        users = mock(UserRepository.class); loans = mock(LoanRepository.class);
        var configuration = mock(LibraryConfigurationService.class);
        service = new LoanService(books, reservations, copies, cards, users, loans, configuration,
                Clock.fixed(at.toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "LIBRARIAN")));
        var type = new CardType(); type.setName("Sinh viên"); type.setMaxBooks(5); type.setLoanDays(14);
        card = new LibraryCard(); card.setCardNumber("TV-12"); card.setUser(user(12L, "READER"));
        card.setCardType(type); card.setIssuedAt(at.toLocalDate().minusDays(1));
        card.setExpiresAt(at.toLocalDate().plusDays(30));
        when(cards.findByCardNumberWithDetails("TV-12")).thenReturn(Optional.of(card));
        when(loans.findReaderIdByCardNumber("TV-12")).thenReturn(Optional.of(12L));
        when(loans.lockDirectLoanCard("TV-12")).thenReturn(Optional.of(12L));
        when(configuration.calculateLoanDates(at, 14, "Sinh viên")).thenReturn(new LoanDatePreviewResponse(
                at.toLocalDate(), "Sinh viên", 14, at.toLocalDate().plusDays(14), at.toLocalDate().plusDays(14),
                at.plusDays(14), false, List.of()));
        for (long id = 1; id <= 2; id++) {
            var book = new Book(); book.setId(50L); book.setTitle("Sách Java");
            when(books.findForReservation(50L)).thenReturn(Optional.of(book));
            var copy = mock(BookCopy.class); when(copy.getId()).thenReturn(id);
            when(copy.getBook()).thenReturn(book); when(copy.getBarcode()).thenReturn("BC-" + id);
            when(copy.getStatus()).thenReturn("AVAILABLE");
            when(copies.findForStatusChange(id)).thenReturn(Optional.of(copy));
            when(loans.findCopyIdentity("BC-" + id)).thenReturn(Optional.of(new LoanRepository.CopyIdentity(id, 50L)));
        }
        when(loans.insertDirect(eq(12L), eq(3L), anyString(), eq(at), eq(key), anyString())).thenReturn(80L);
        when(loans.findHeaderForStaff(80L)).thenReturn(Optional.of(new LoanDetailResponse(
                80L, "PM-TEST", null, 12L, "Bạn đọc", 3L, "Thủ thư", at, List.of())));
        when(loans.findItemsForStaff(80L)).thenReturn(List.of(new LoanDetailResponse.Item(
                101L, 1L, "BC-1", 50L, "Sách Java", at, at.plusDays(14))));
    }

    private CreateDirectLoanRequest request(String... barcodes) {
        return new CreateDirectLoanRequest(key, " TV-12 ", Arrays.asList(barcodes));
    }
    private void rejects(CreateDirectLoanRequest request, String code) {
        assertThatThrownBy(() -> service.createDirectLoan(request, 3L)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
        verify(loans, never()).insertDirect(any(), any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }

    @Test void createsOneHeaderAndExactlyOneItemPerCopyWithSameDatesAndStaff() {
        var result = service.createDirectLoan(request(" BC-2 ", "BC-1"), 3L);
        assertThat(result.loan().id()).isEqualTo(80L);
        assertThat(result.loan().createdById()).isEqualTo(3L);
        var order = inOrder(loans, reservations, books, copies);
        order.verify(loans).lockDirectRequest(key);
        order.verify(reservations).lockReaderForCreation(12L);
        order.verify(loans).lockDirectLoanCard("TV-12");
        order.verify(books).findForReservation(50L);
        order.verify(copies).findForStatusChange(1L);
        order.verify(copies).findForStatusChange(2L);
        order.verify(loans).insertDirect(eq(12L), eq(3L), anyString(), eq(at), eq(key), matches("[0-9a-f]{64}"));
        order.verify(loans).insertItem(80L, 2L, at, at.plusDays(14));
        order.verify(loans).insertItem(80L, 1L, at, at.plusDays(14));
        verify(loans, times(2)).insertItem(any(), any(), any(), any());
        verify(copies, never()).save(any());
    }

    @Test void invalidEmptyDuplicateAndOversizePayloadsNeverWrite() {
        rejects(null, "INVALID_LOAN_DRAFT");
        rejects(new CreateDirectLoanRequest(null, "TV-12", List.of("BC-1")), "INVALID_LOAN_DRAFT");
        rejects(request(), "INVALID_LOAN_DRAFT");
        rejects(request("BC-1", " BC-1 "), "DUPLICATE_LOAN_DRAFT_BARCODE");
        rejects(request(" "), "INVALID_BARCODE");
        rejects(request("x".repeat(101)), "INVALID_BARCODE");
        rejects(new CreateDirectLoanRequest(key, "", List.of("BC-1")), "INVALID_CARD_NUMBER");
    }

    @Test void checksCardAccountAndQuotaAgainAfterCopyLocks() {
        card.setStatus("LOCKED"); rejects(request("BC-1"), "LIBRARY_CARD_LOCKED");
        card.setStatus("ACTIVE"); card.getUser().setStatus("DISABLED");
        rejects(request("BC-1"), "READER_ACCOUNT_INACTIVE");
        card.getUser().setStatus("ACTIVE"); when(loans.countUnreturnedBooksForReader(12L)).thenReturn(4L);
        rejects(request("BC-1", "BC-2"), "LOAN_DRAFT_LIMIT_EXCEEDED");
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(5L);
        rejects(request("BC-1"), "LOAN_LIMIT_REACHED");
    }

    @Test void overdueAppearingAfterPreviewBlocksAtomicConfirmation() {
        assertThat(service.readerEligibility("TV-12", 3L).eligible()).isTrue();
        when(loans.countOverdueUnreturnedLoansForReader(12L, at.toLocalDate())).thenReturn(1L);
        rejects(request("BC-1"), "LOAN_OVERDUE_UNRETURNED");
    }

    @Test void missingChangedAndUnreturnedCopiesNeverWrite() {
        rejects(request("UNKNOWN"), "LOAN_DRAFT_COPY_NOT_FOUND");
        var copy = copies.findForStatusChange(2L).orElseThrow();
        when(copy.getStatus()).thenReturn("REPAIR");
        rejects(request("BC-1", "BC-2"), "LOAN_DRAFT_COPY_NOT_AVAILABLE");
        when(copy.getStatus()).thenReturn("AVAILABLE"); when(copies.hasUnreturnedLoan(2L)).thenReturn(true);
        rejects(request("BC-1", "BC-2"), "LOAN_DRAFT_COPY_NOT_AVAILABLE");
    }

    @Test void aHoldCreatedAfterPreviewBlocksEvenAnAvailableCopy() {
        var hold = new BookReservation(); hold.setId(42L); hold.setReader(user(99L, "READER"));
        hold.setPickupDeadline(at.plusDays(1)); hold.setStatus("READY_FOR_PICKUP");
        when(reservations.findEffectiveHoldForCopy(2L, at)).thenReturn(Optional.of(hold));
        rejects(request("BC-1", "BC-2"), "LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER");
        hold.setReader(card.getUser());
        rejects(request("BC-1", "BC-2"), "LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER");
    }

    @Test void retryReturnsSavedLoanWithoutRevalidatingCopiesOrInsertingAgain() {
        var first = service.createDirectLoan(request("BC-1"), 3L);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(loans).insertDirect(eq(12L), eq(3L), anyString(), eq(at), eq(key), captor.capture());
        when(loans.findDirectRequest(key)).thenReturn(Optional.of(new LoanRepository.DirectRequest(80L, 3L, captor.getValue())));
        clearInvocations(loans, copies, books, reservations);
        var retry = service.createDirectLoan(request(" BC-1 "), 3L);
        assertThat(retry.loan()).isEqualTo(first.loan());
        verifyNoInteractions(copies, books, reservations);
        verify(loans, never()).insertDirect(any(), any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }

    @Test void reusedKeyWithDifferentPayloadOrActorIsRejected() {
        when(loans.findDirectRequest(key)).thenReturn(Optional.of(new LoanRepository.DirectRequest(80L, 3L, "different")));
        rejects(request("BC-1"), "DIRECT_LOAN_REQUEST_REUSED");
        when(loans.findDirectRequest(key)).thenReturn(Optional.of(new LoanRepository.DirectRequest(80L, 99L, "different")));
        rejects(request("BC-1"), "DIRECT_LOAN_REQUEST_REUSED");
    }

    @Test void readerRoleCannotConfirm() {
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "READER")));
        rejects(request("BC-1"), "STAFF_ROLE_REQUIRED");
        verifyNoInteractions(books, copies, reservations, cards, loans);
    }

    @Test void aSecondItemWriteFailureEscapesAsRollbackErrorWithoutReadingSuccess() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("test second item"))
                .when(loans).insertItem(80L, 2L, at, at.plusDays(14));
        assertThatThrownBy(() -> service.createDirectLoan(request("BC-1", "BC-2"), 3L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("DIRECT_LOAN_SAVE_FAILED");
                    assertThat(error.getCause()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                });
        verify(loans).insertItem(80L, 1L, at, at.plusDays(14));
        verify(loans, never()).findHeaderForStaff(anyLong());
    }
}
