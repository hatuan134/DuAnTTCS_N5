package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** S3-05.3: the counter is shared by all copies of a single loan. */
class ReaderRenewalQuotaServiceTest {
    private static final Long ITEM = 100L;
    private static final Long READER = 12L;
    private static final Long LOAN = 50L;
    private final LoanRepository loans = mock(LoanRepository.class);
    private final BookReservationRepository reservations = mock(BookReservationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private LoanService service;

    @BeforeEach void setUp() {
        service = new LoanService(mock(BookRepository.class), reservations,
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                mock(LibraryConfigurationService.class), Clock.fixed(
                        OffsetDateTime.parse("2026-10-08T09:00:00Z").toInstant(), ZoneOffset.UTC));
        User reader = new User(); reader.setId(READER); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); reader.setRole(role);
        when(users.findById(READER)).thenReturn(Optional.of(reader));
        when(loans.findRenewalCandidateForReader(ITEM, READER)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-15T09:00:00Z"), null)));
        when(loans.incrementRenewalCountIfAllowed(LOAN, READER)).thenReturn(1);
    }

    private void quota(int used, Integer maximum) {
        when(loans.findRenewalPolicyForReader(ITEM, READER)).thenReturn(
                Optional.of(new LoanRepository.RenewalPolicy(LOAN, used, maximum)));
    }

    private void rejected(String code) {
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(code);
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
        verify(loans, never()).incrementRenewalCountIfAllowed(anyLong(), anyLong());
    }

    @Test void neverRenewedBeforeCountsFirstApprovedRequest() {
        quota(0, 2);
        var result = service.checkMyLoanRenewal(ITEM, READER);
        assertThat(result.eligible()).isTrue();
        assertThat(result.renewalsUsed()).isEqualTo(1);
        assertThat(result.maxRenewals()).isEqualTo(2);
        assertThat(result.message()).contains("1/2").contains("chưa thay đổi");
        verify(loans).incrementRenewalCountIfAllowed(LOAN, READER);
    }

    @Test void usedBelowLimitCountsExactlyOneMore() {
        quota(1, 2);
        var result = service.checkMyLoanRenewal(ITEM, READER);
        assertThat(result.renewalsUsed()).isEqualTo(2);
        assertThat(result.message()).contains("2/2");
        verify(loans, times(1)).incrementRenewalCountIfAllowed(LOAN, READER);
    }

    @Test void reachedLimitDoesNotChangeCounter() {
        quota(2, 2);
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RENEWAL_LIMIT_REACHED");
                    assertThat(error.getMessage()).contains("2/2");
                });
        verify(loans, never()).incrementRenewalCountIfAllowed(anyLong(), anyLong());
    }

    @Test void existingOverLimitDataDoesNotChangeCounter() {
        quota(3, 2);
        rejected("RENEWAL_LIMIT_REACHED");
    }

    @Test void missingOrUnconfiguredCardTypeIsFailClosed() {
        when(loans.findRenewalPolicyForReader(ITEM, READER)).thenReturn(Optional.empty());
        rejected("RENEWAL_POLICY_MISSING");
        quota(0, null);
        rejected("RENEWAL_POLICY_MISSING");
        quota(0, 0);
        rejected("RENEWAL_POLICY_MISSING");
    }

    @Test void simultaneousChangeExhaustingQuotaIsRejectedByAtomicUpdate() {
        quota(1, 2);
        when(loans.incrementRenewalCountIfAllowed(LOAN, READER)).thenReturn(0);
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("RENEWAL_LIMIT_REACHED"));
    }

    @Test void waitingReaderRejectionLeavesCounterUntouched() {
        quota(0, 2);
        when(reservations.existsOtherEffectiveReservationForLoanItem(eq(ITEM), eq(READER), any()))
                .thenReturn(true);
        rejected("RENEWAL_BLOCKED_BY_RESERVATION");
    }

    @Test void alreadyReturnedLoanDoesNotChangeCounter() {
        quota(0, 2);
        when(loans.findRenewalCandidateForReader(ITEM, READER)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-15T09:00:00Z"),
                        OffsetDateTime.parse("2026-10-08T08:00:00Z"))));
        rejected("LOAN_ALREADY_RETURNED");
    }
}
