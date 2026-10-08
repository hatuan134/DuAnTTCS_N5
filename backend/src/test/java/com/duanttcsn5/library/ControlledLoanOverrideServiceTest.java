package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanRejectionLogService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** S3-03.5: an override authorizes only one committed loan, never a standing exemption. */
class ControlledLoanOverrideServiceTest {
    private BookRepository books;
    private BookCopyRepository copies;
    private BookReservationRepository reservations;
    private LibraryCardRepository cards;
    private UserRepository users;
    private LoanRepository loans;
    private LoanRejectionLogService audit;
    private LoanService service;
    private LibraryCard card;
    private final OffsetDateTime at = OffsetDateTime.parse("2026-10-08T09:00:00+07:00");

    private static User user(long id, String roleCode) {
        var user = new User();
        user.setId(id); user.setStatus("ACTIVE"); user.setFullName(roleCode + " " + id);
        var role = new Role(); role.setCode(roleCode); user.setRole(role);
        return user;
    }

    @BeforeEach void setup() {
        books = mock(BookRepository.class);
        copies = mock(BookCopyRepository.class);
        reservations = mock(BookReservationRepository.class);
        cards = mock(LibraryCardRepository.class);
        users = mock(UserRepository.class);
        loans = mock(LoanRepository.class);
        audit = mock(LoanRejectionLogService.class);
        var config = mock(LibraryConfigurationService.class);
        service = new LoanService(books, reservations, copies, cards, users, loans, config,
                Clock.fixed(at.toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")), audit);
        when(users.findById(5L)).thenReturn(Optional.of(user(5L, "LIBRARY_MANAGER")));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, "LIBRARIAN")));
        when(users.findById(4L)).thenReturn(Optional.of(user(4L, "ADMIN")));
        when(users.findById(6L)).thenReturn(Optional.of(user(6L, "READER")));

        var type = new CardType(); type.setName("Sinh viên"); type.setMaxBooks(5); type.setLoanDays(14);
        card = new LibraryCard(); card.setCardNumber("TV-12"); card.setUser(user(12L, "READER"));
        card.setStatus("ACTIVE"); card.setCardType(type);
        card.setIssuedAt(at.toLocalDate().minusDays(1));
        card.setExpiresAt(at.toLocalDate().plusDays(30));
        when(cards.findByCardNumberWithDetails("TV-12")).thenReturn(Optional.of(card));
        when(loans.findReaderIdByCardNumber("TV-12")).thenReturn(Optional.of(12L));
        when(loans.lockDirectLoanCard("TV-12")).thenReturn(Optional.of(12L));
        when(loans.countUnreturnedBooksForReader(12L)).thenReturn(5L);
        when(config.calculateLoanDates(eq(at), eq(14), eq("Sinh viên"))).thenReturn(
                new LoanDatePreviewResponse(at.toLocalDate(), "Sinh viên", 14,
                        at.toLocalDate().plusDays(14), at.toLocalDate().plusDays(14),
                        at.plusDays(14), false, List.of()));
        var book = new Book(); book.setId(50L); book.setTitle("Sách Java");
        when(books.findForReservation(50L)).thenReturn(Optional.of(book));
        var copy = mock(BookCopy.class);
        when(copy.getId()).thenReturn(1L);
        when(copy.getBook()).thenReturn(book);
        when(copy.getBarcode()).thenReturn("BC-1");
        when(copy.getStatus()).thenReturn("AVAILABLE");
        when(copies.findForStatusChange(1L)).thenReturn(Optional.of(copy));
        when(loans.findCopyIdentity("BC-1")).thenReturn(Optional.of(new LoanRepository.CopyIdentity(1L, 50L)));
        when(loans.insertDirect(eq(12L), eq(5L), anyString(), eq(at), any(UUID.class), anyString()))
                .thenReturn(80L);
        when(loans.findHeaderForStaff(80L)).thenReturn(Optional.of(new LoanDetailResponse(
                80L, "PM-80", null, 12L, "Bạn đọc", 5L, "Quản lý", at, List.of())));
        when(loans.findItemsForStaff(80L)).thenReturn(List.of(new LoanDetailResponse.Item(
                101L, 1L, "BC-1", 50L, "Sách Java", at, at.plusDays(14))));
    }

    private static CreateDirectLoanRequest request(UUID key, boolean override, String reason) {
        return new CreateDirectLoanRequest(key, "TV-12", List.of("BC-1"), override, reason);
    }

    private void rejected(UUID key, Long actor, boolean override, String reason, String expectedCode) {
        assertThatThrownBy(() -> service.createDirectLoan(request(key, override, reason), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo(expectedCode));
        verify(loans, never()).insertItem(any(), any(), any(), any());
        verify(audit, never()).logOverride(any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt(), anyLong(), any(), any());
    }

    @Test void librarianAndAdministratorCannotOverrideEvenWithReason() {
        rejected(UUID.randomUUID(), 3L, true, "Tôi đồng ý", "MANAGER_OVERRIDE_REQUIRED");
        rejected(UUID.randomUUID(), 4L, true, "Tôi đồng ý", "MANAGER_OVERRIDE_REQUIRED");
    }

    @Test void reasonIsMandatoryAndCannotBeSuppliedWithoutExplicitApproval() {
        rejected(UUID.randomUUID(), 5L, true, "   ", "OVERRIDE_REASON_REQUIRED");
        rejected(UUID.randomUUID(), 5L, false, "Lý do không được âm thầm gửi", "OVERRIDE_FLAG_REQUIRED");
        rejected(UUID.randomUUID(), 5L, true, "X".repeat(501), "OVERRIDE_REASON_REQUIRED");
    }

    @Test void managerCanCreateOneLoanAndAuditExactViolationAndExplanation() {
        UUID id = UUID.randomUUID();
        var result = service.createDirectLoan(request(id, true, "  Cho phép ngoại lệ một lượt  "), 5L);
        assertThat(result.loan().id()).isEqualTo(80L);
        verify(loans).insertItem(80L, 1L, at, at.plusDays(14));
        verify(audit).logOverride(any(UUID.class), eq("DIRECT_CONFIRM"), eq(12L), eq("READER 12"),
                eq("TV-12"), eq(5L), isNull(), eq(5L), eq(5), eq(80L),
                eq("Cho phép ngoại lệ một lượt"), argThat(reasons ->
                        reasons.stream().anyMatch(r -> r.code().equals("LOAN_LIMIT_REACHED"))));
        verify(audit, never()).log(any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt(), any());
    }

    @Test void secondRequestStillRechecksOriginalPolicyAndGetsBlocked() {
        service.createDirectLoan(request(UUID.randomUUID(), true, "Cho phép một lượt"), 5L);
        clearInvocations(loans);
        assertThatThrownBy(() -> service.createDirectLoan(request(UUID.randomUUID(), false, null), 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LOAN_LIMIT_REACHED"));
        verify(loans, never()).insertDirect(any(), any(), any(), any(), any(), any());
        verify(loans, never()).insertItem(any(), any(), any(), any());
    }

    @Test void approvedRetryAfterBlockedAttemptKeepsBothAuditEvents() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.createDirectLoan(request(id, false, null), 5L))
                .isInstanceOf(ApiException.class);
        service.createDirectLoan(request(id, true, "Phê duyệt sau khi xem vi phạm"), 5L);
        var eventIds = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(audit).log(eq(id), eq("DIRECT_CONFIRM"), anyLong(), anyString(), anyString(),
                eq(5L), isNull(), anyLong(), anyInt(), any());
        verify(audit).logOverride(eventIds.capture(), eq("DIRECT_CONFIRM"), eq(12L), anyString(),
                eq("TV-12"), eq(5L), isNull(), eq(5L), eq(5), eq(80L), anyString(), any());
        assertThat(eventIds.getValue()).isNotEqualTo(id);
    }

    @Test void revokedCardIsNeverOverridableEvenWithLoanLimitViolation() {
        card.setStatus("LOCKED");
        rejected(UUID.randomUUID(), 5L, true, "Cho phép mượn", "OVERRIDE_NOT_ALLOWED");
    }

    @Test void librarianCannotPreviewAnOverQuotaDraftUsingOverrideFlag() {
        assertThatThrownBy(() -> service.previewDirectLoanItem(
                new AddDirectLoanItemRequest("TV-12", "BC-1", List.of(), true), 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("MANAGER_OVERRIDE_REQUIRED"));
    }

    @Test void readerCannotRequestManagerPreviewOverride() {
        assertThatThrownBy(() -> service.previewDirectLoanItem(
                new AddDirectLoanItemRequest("TV-12", "BC-1", List.of(), true), 6L))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STAFF_ROLE_REQUIRED"));
    }
}
