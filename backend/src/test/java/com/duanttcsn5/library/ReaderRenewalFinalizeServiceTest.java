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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Transaction order and failures; PostgreSQL writes are tested separately. */
class ReaderRenewalFinalizeServiceTest {
    private static final Long ITEM = 105L, READER = 12L, LOAN = 55L;
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2026-10-14T17:00:00+07:00");
    private static final OffsetDateTime NEW = OffsetDateTime.parse("2026-10-21T17:00:00+07:00");
    private final LoanRepository loans = mock(LoanRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LibraryConfigurationService calendar = mock(LibraryConfigurationService.class);
    private LoanService service;

    @BeforeEach void setup() {
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans, calendar,
                Clock.fixed(OffsetDateTime.parse("2026-10-08T09:00:00Z").toInstant(), ZoneOffset.UTC));
        var user = new User(); user.setId(READER); user.setStatus("ACTIVE");
        var role = new Role(); role.setCode("READER"); user.setRole(role);
        when(users.findById(READER)).thenReturn(Optional.of(user));
        when(loans.findRenewalCandidateForReader(ITEM, READER))
                .thenReturn(Optional.of(new LoanRepository.RenewalCandidate(OLD, null)));
        when(loans.findRenewalPolicyForReader(ITEM, READER))
                .thenReturn(Optional.of(new LoanRepository.RenewalPolicy(LOAN, 1, 3, 7)));
        when(calendar.calculateRenewalDueAt(OLD, 7)).thenReturn(NEW);
        when(loans.incrementRenewalCountIfAllowed(LOAN, READER)).thenReturn(1);
    }

    @Test void dueDateIsWrittenBeforeCounterAndReturnedForImmediateDisplay() {
        var response = service.checkMyLoanRenewal(ITEM, READER);
        assertThat(response.eligible()).isTrue();
        assertThat(response.dueAt()).isEqualTo(NEW);
        assertThat(response.renewalsUsed()).isEqualTo(2);
        assertThat(response.message()).contains("Gia hạn thành công").contains("21/10/2026");
        var ordered = inOrder(loans);
        ordered.verify(loans).updateDueAtForRenewal(ITEM, READER, OLD, NEW);
        ordered.verify(loans).incrementRenewalCountIfAllowed(LOAN, READER);
    }

    @Test void calendarFailureCannotWriteAnyChanges() {
        when(calendar.calculateRenewalDueAt(OLD, 7)).thenThrow(new IllegalStateException("Lỗi lịch"));
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER)).isInstanceOf(IllegalStateException.class);
        verify(loans, never()).updateDueAtForRenewal(anyLong(), anyLong(), any(), any());
        verify(loans, never()).incrementRenewalCountIfAllowed(anyLong(), anyLong());
    }

    @Test void invalidRenewalDaysCannotWriteAnyChanges() {
        when(loans.findRenewalPolicyForReader(ITEM, READER))
                .thenReturn(Optional.of(new LoanRepository.RenewalPolicy(LOAN, 1, 3, 0)));
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RENEWAL_DAYS_NOT_CONFIGURED"));
        verify(loans, never()).updateDueAtForRenewal(anyLong(), anyLong(), any(), any());
    }

    @Test void quotaUpdateFailureThrowsSoTransactionalProxyCanRollbackBothWrites() {
        when(loans.incrementRenewalCountIfAllowed(LOAN, READER)).thenReturn(0);
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("RENEWAL_LIMIT_REACHED"));
        var ordered = inOrder(loans);
        ordered.verify(loans).updateDueAtForRenewal(ITEM, READER, OLD, NEW);
        ordered.verify(loans).incrementRenewalCountIfAllowed(LOAN, READER);
    }
}
