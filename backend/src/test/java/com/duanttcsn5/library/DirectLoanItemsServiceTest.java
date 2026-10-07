package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LoanService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectLoanItemsServiceTest {
    private BookCopyRepository copies;
    private LoanRepository loans;
    private BookReservationRepository reservations;
    private UserRepository users;
    private LoanService service;
    private LibraryCard card;

    @BeforeEach void setup() {
        copies = mock(BookCopyRepository.class); loans = mock(LoanRepository.class);
        reservations = mock(BookReservationRepository.class); users = mock(UserRepository.class);
        var cards = mock(LibraryCardRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        service = new LoanService(mock(BookRepository.class), reservations, copies, cards, users,
                loans, mock(LibraryConfigurationService.class), clock);
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "LIBRARIAN")));
        var type = new CardType(); type.setName("Thẻ sinh viên"); type.setMaxBooks(5);
        card = new LibraryCard(); card.setCardNumber("TV-0012"); card.setUser(user(12L, "READER"));
        card.setCardType(type); card.setIssuedAt(LocalDate.of(2026, 1, 1));
        card.setExpiresAt(LocalDate.of(2026, 12, 31));
        when(cards.findByCardNumberWithDetails("TV-0012")).thenReturn(Optional.of(card));
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(2L);
        for (long id = 1; id <= 4; id++) {
            Book book = mock(Book.class); when(book.getId()).thenReturn(50L);
            when(book.getTitle()).thenReturn("Lập trình Java");
            BookCopy copy = mock(BookCopy.class); when(copy.getId()).thenReturn(id);
            when(copy.getBarcode()).thenReturn("BC-" + id); when(copy.getBook()).thenReturn(book);
            when(copy.getStatus()).thenReturn("AVAILABLE");
            when(copies.findByBarcode("BC-" + id)).thenReturn(Optional.of(copy));
        }
    }

    private User user(Long id, String code) {
        User user = new User(); user.setId(id); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(code); user.setRole(role); return user;
    }
    private AddDirectLoanItemRequest request(String barcode, String... selected) {
        return new AddDirectLoanItemRequest(" TV-0012 ", barcode, List.of(selected));
    }
    private void rejects(AddDirectLoanItemRequest request, String code) {
        assertThatThrownBy(() -> service.previewDirectLoanItem(request, 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test void singleAndConsecutiveBarcodesReturnOneCopyWithTitleWithoutWriting() {
        var first = service.previewDirectLoanItem(request(" BC-1 "), 3L);
        var second = service.previewDirectLoanItem(request("BC-2", "BC-1"), 3L);
        var third = service.previewDirectLoanItem(request("BC-3", "BC-1", "BC-2"), 3L);
        assertThat(first.bookCopyId()).isEqualTo(1L);
        assertThat(first.barcode()).isEqualTo("BC-1");
        assertThat(second.bookCopyId()).isEqualTo(2L);
        assertThat(third.bookCopyId()).isEqualTo(3L);
        assertThat(third.bookTitle()).isEqualTo("Lập trình Java");
        assertThat(third.remainingBooks()).isEqualTo(3L);
        verify(reservations, times(3)).findEffectiveHoldForCopy(anyLong(), any(OffsetDateTime.class));
        verifyNoMoreInteractions(reservations);
        verify(loans, times(3)).countUnreturnedBooksForReader(12L);
        verifyNoMoreInteractions(loans);
        verify(copies, times(3)).findByBarcode(anyString());
        verifyNoMoreInteractions(copies);
    }
    @Test void duplicatesAreRejectedAfterTrimmingAndWithoutCopyLookup() {
        rejects(request(" BC-1 ", "BC-1"), "DUPLICATE_LOAN_DRAFT_BARCODE");
        rejects(request("BC-2", "BC-1", " BC-1 "), "DUPLICATE_LOAN_DRAFT_BARCODE");
        verifyNoInteractions(copies, loans);
    }
    @Test void removingThenReaddingUsesOnlyTheCurrentDraft() {
        var result = service.previewDirectLoanItem(request("BC-1", "BC-2"), 3L);
        assertThat(result.barcode()).isEqualTo("BC-1");
    }
    @Test void blocksNextRowAtQuotaAndRechecksChangedQuota() {
        rejects(request("BC-4", "BC-1", "BC-2", "BC-3"), "LOAN_DRAFT_LIMIT_EXCEEDED");
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(4L);
        rejects(request("BC-2", "BC-1"), "LOAN_DRAFT_LIMIT_EXCEEDED");
        verifyNoInteractions(copies);
    }
    @Test void invalidReaderAndUnknownBarcodeDoNotWrite() {
        rejects(request("UNKNOWN"), "LOAN_DRAFT_COPY_NOT_FOUND");
        card.setStatus("LOCKED");
        rejects(request("BC-1"), "LIBRARY_CARD_LOCKED");
        verifyNoInteractions(reservations);
    }
    @Test void validatesDraftAndBarcodeInServiceAsWellAsController() {
        rejects(null, "INVALID_LOAN_DRAFT");
        rejects(new AddDirectLoanItemRequest("TV-0012", "BC-1", null), "INVALID_LOAN_DRAFT");
        rejects(request("BC-1", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"), "INVALID_LOAN_DRAFT");
        for (String code : new String[]{null, " ", "x".repeat(101)}) rejects(request(code), "INVALID_BARCODE");
        rejects(request("BC-1", " "), "INVALID_BARCODE");
        verifyNoInteractions(copies, loans);
    }
    @Test void unavailableCopiesReportTheirCurrentStatusWithoutWriting() {
        String[][] statuses = {{"BORROWED", "Đang mượn"}, {"REPAIR", "Đang sửa chữa"},
                {"HELD", "Đang giữ cho đặt trước"}, {"REMOVED", "Đã loại khỏi kho"},
                {"LOST", "Mất"}, {"DAMAGED", "Hư hỏng"}, {"UNKNOWN", "UNKNOWN"},
                {null, "Chưa xác định"}, {" ", "Chưa xác định"}};
        var copy = copies.findByBarcode("BC-2").orElseThrow();
        for (String[] status : statuses) {
            when(copy.getStatus()).thenReturn(status[0]);
            assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-2", "BC-1"), 3L))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(409);
                        assertThat(e.getCode()).isEqualTo("LOAN_DRAFT_COPY_NOT_AVAILABLE");
                        assertThat(e.getMessage()).contains("Trạng thái hiện tại: " + status[1]);
                    });
        }
        verify(reservations, times(statuses.length)).findEffectiveHoldForCopy(anyLong(), any(OffsetDateTime.class));
        verifyNoMoreInteractions(reservations);
        verify(loans, times(statuses.length)).countUnreturnedBooksForReader(12L);
        verifyNoMoreInteractions(loans);
        verify(copies, never()).save(any());
    }

    @Test void failedMiddleBarcodeDoesNotAffectLaterChecksOrValidDraft() {
        var selected = new java.util.ArrayList<String>();
        selected.add(service.previewDirectLoanItem(request("BC-1"), 3L).barcode());
        rejects(request("UNKNOWN", selected.toArray(String[]::new)), "LOAN_DRAFT_COPY_NOT_FOUND");
        var bad = copies.findByBarcode("BC-2").orElseThrow();
        when(bad.getStatus()).thenReturn("REPAIR");
        rejects(request("BC-2", selected.toArray(String[]::new)), "LOAN_DRAFT_COPY_NOT_AVAILABLE");
        selected.add(service.previewDirectLoanItem(request("BC-3", selected.toArray(String[]::new)), 3L).barcode());
        assertThat(selected).containsExactly("BC-1", "BC-3");
        // Replacing/deleting the failed line needs no database cleanup.
        when(bad.getStatus()).thenReturn("AVAILABLE");
        assertThat(service.previewDirectLoanItem(request("BC-2", selected.toArray(String[]::new)), 3L).barcode())
                .isEqualTo("BC-2");
        verify(reservations, times(4)).findEffectiveHoldForCopy(anyLong(), any(OffsetDateTime.class));
        verifyNoMoreInteractions(reservations);
        verify(copies, never()).save(any());
    }

    @Test void rejectsReaderRoleAndInactiveStaffBeforeLookup() {
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "READER")));
        rejects(request("BC-1"), "STAFF_ROLE_REQUIRED");
        var staff = user(3L, "LIBRARIAN"); staff.setStatus("LOCKED");
        when(users.findById(3L)).thenReturn(Optional.of(staff));
        rejects(request("BC-1"), "STAFF_ROLE_REQUIRED");
        assertThatThrownBy(() -> service.previewDirectLoanItem(request("BC-1"), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(401));
        verifyNoInteractions(copies, loans);
    }
}
