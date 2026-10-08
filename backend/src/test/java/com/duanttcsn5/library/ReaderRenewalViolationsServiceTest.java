package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** S3-05.4: an unrelated overdue loan and outstanding fees independently block renewal. */
class ReaderRenewalViolationsServiceTest {
    private static final Long ITEM = 100L;
    private static final Long READER = 12L;
    private static final Long CURRENT_LOAN = 50L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private final LoanRepository loans = mock(LoanRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookReservationRepository reservations = mock(BookReservationRepository.class);
    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService(mock(BookRepository.class), reservations,
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class),
                users, loans, RenewalCalendarStub.mockCalendar(),
                Clock.fixed(OffsetDateTime.parse("2026-10-08T09:00:00Z").toInstant(), ZoneOffset.UTC));
        User user = new User();
        user.setId(READER);
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("READER");
        user.setRole(role);
        when(users.findById(READER)).thenReturn(Optional.of(user));
        when(loans.findRenewalCandidateForReader(ITEM, READER)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(
                        OffsetDateTime.parse("2026-10-15T09:00:00Z"), null)));
        when(loans.findRenewalPolicyForReader(ITEM, READER)).thenReturn(Optional.of(
                new LoanRepository.RenewalPolicy(CURRENT_LOAN, 0, 3, 7)));
        when(loans.incrementRenewalCountIfAllowed(CURRENT_LOAN, READER)).thenReturn(1);
        when(loans.sumUnpaidFeesForReader(READER)).thenReturn(BigDecimal.ZERO);
    }

    private ApiException expectBlocked() {
        final ApiException[] captured = new ApiException[1];
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_VIOLATIONS");
                    captured[0] = error;
                });
        verify(loans, never()).incrementRenewalCountIfAllowed(anyLong(), anyLong());
        return captured[0];
    }

    @Test void withoutViolationsAnApprovedRequestIncrementsOnce() {
        var response = service.checkMyLoanRenewal(ITEM, READER);
        assertThat(response.eligible()).isTrue();
        assertThat(response.renewalsUsed()).isEqualTo(1);
        verify(loans).countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY);
        verify(loans).sumUnpaidFeesForReader(READER);
        verify(loans, times(1)).incrementRenewalCountIfAllowed(CURRENT_LOAN, READER);
    }

    @Test void oneDifferentOverdueLoanBlocksRenewal() {
        when(loans.countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY))
                .thenReturn(1L);
        assertThat(expectBlocked().getMessage()).contains("1 phiếu mượn khác quá hạn")
                .doesNotContain("nợ phí chưa thanh toán");
    }

    @Test void multipleDifferentOverdueLoansBlockRenewal() {
        when(loans.countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY))
                .thenReturn(3L);
        assertThat(expectBlocked().getMessage()).contains("3 phiếu mượn khác quá hạn");
    }

    @Test void positiveRemainingDebtBlocksRenewal() {
        when(loans.sumUnpaidFeesForReader(READER)).thenReturn(new BigDecimal("35000"));
        assertThat(expectBlocked().getMessage()).contains("nợ phí chưa thanh toán")
                .contains("35.000").doesNotContain("phiếu mượn khác quá hạn");
    }

    @Test void bothViolationsAreShownTogether() {
        when(loans.countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY))
                .thenReturn(2L);
        when(loans.sumUnpaidFeesForReader(READER)).thenReturn(new BigDecimal("12500"));
        assertThat(expectBlocked().getMessage())
                .contains("2 phiếu mượn khác quá hạn")
                .contains("nợ phí chưa thanh toán")
                .contains("12.500")
                .contains("Hạn trả và số lần gia hạn đã dùng không thay đổi");
    }

    @Test void returningTheOtherOverdueLoansAllowsRetry() {
        when(loans.countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY))
                .thenReturn(1L, 0L);
        expectBlocked();
        assertThat(service.checkMyLoanRenewal(ITEM, READER).eligible()).isTrue();
        verify(loans, times(1)).incrementRenewalCountIfAllowed(CURRENT_LOAN, READER);
    }

    @Test void payingAllFeesAllowsRetry() {
        when(loans.sumUnpaidFeesForReader(READER)).thenReturn(new BigDecimal("5000"), BigDecimal.ZERO);
        expectBlocked();
        assertThat(service.checkMyLoanRenewal(ITEM, READER).eligible()).isTrue();
        verify(loans, times(1)).incrementRenewalCountIfAllowed(CURRENT_LOAN, READER);
    }

    @Test void clearingOnlyOneOfTwoViolationsStillBlocksUntilBothResolved() {
        when(loans.countOtherOverdueUnreturnedLoansForReader(READER, CURRENT_LOAN, TODAY))
                .thenReturn(1L, 0L, 0L);
        when(loans.sumUnpaidFeesForReader(READER))
                .thenReturn(new BigDecimal("8000"), new BigDecimal("8000"), BigDecimal.ZERO);
        assertThat(expectBlocked().getMessage()).contains("quá hạn").contains("nợ phí");
        assertThat(expectBlocked().getMessage()).doesNotContain("phiếu mượn khác quá hạn").contains("nợ phí");
        assertThat(service.checkMyLoanRenewal(ITEM, READER).eligible()).isTrue();
        verify(loans, times(1)).incrementRenewalCountIfAllowed(CURRENT_LOAN, READER);
    }
}
