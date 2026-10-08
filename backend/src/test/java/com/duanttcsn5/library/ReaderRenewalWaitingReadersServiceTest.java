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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReaderRenewalWaitingReadersServiceTest {
    private static final Long READER_ID = 12L;
    private static final Long ITEM_ID = 100L;
    private static final OffsetDateTime CHECKED_AT = OffsetDateTime.parse("2026-10-08T09:00:00Z");
    private final LoanRepository loans = mock(LoanRepository.class);
    private final BookReservationRepository reservations = mock(BookReservationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService(mock(BookRepository.class), reservations,
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                mock(LibraryConfigurationService.class), Clock.fixed(CHECKED_AT.toInstant(), ZoneOffset.UTC));
        User reader = new User();
        reader.setId(READER_ID);
        reader.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("READER");
        reader.setRole(role);
        when(users.findById(READER_ID)).thenReturn(Optional.of(reader));
        when(loans.findRenewalCandidateForReader(ITEM_ID, READER_ID)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-15T09:00:00Z"), null)));
        when(loans.findRenewalPolicyForReader(ITEM_ID, READER_ID)).thenReturn(
                Optional.of(new LoanRepository.RenewalPolicy(50L, 0, 3)));
        when(loans.incrementRenewalCountIfAllowed(50L, READER_ID)).thenReturn(1);
    }

    @Test
    void noOtherActiveReservationAllowsEligibilityCheckWithoutChangingDueDate() {
        assertThat(service.checkMyLoanRenewal(ITEM_ID, READER_ID).eligible()).isTrue();
        assertThat(service.checkMyLoanRenewal(ITEM_ID, READER_ID).message()).contains("chưa thay đổi");
        verify(reservations, times(2)).existsOtherEffectiveReservationForLoanItem(
                eq(ITEM_ID), eq(READER_ID), eq(OffsetDateTime.parse("2026-10-08T16:00:00+07:00")));
        verify(loans, never()).insertItem(any(), any(), any());
    }

    @Test
    void oneOrManyOtherWaitingReadersBlockWithoutUpdatingDueDate() {
        when(reservations.existsOtherEffectiveReservationForLoanItem(eq(ITEM_ID), eq(READER_ID), any()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM_ID, READER_ID))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getCode()).isEqualTo("RENEWAL_BLOCKED_BY_RESERVATION");
                    assertThat(error.getMessage()).contains("Bạn đọc khác").contains("không thay đổi");
                });
        verify(loans, never()).insertItem(any(), any(), any());
    }

    @Test
    void returnedLoanIsRejectedBeforeQueueCheck() {
        when(loans.findRenewalCandidateForReader(ITEM_ID, READER_ID)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-15T09:00:00Z"), CHECKED_AT)));
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM_ID, READER_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOAN_ALREADY_RETURNED"));
        verifyNoInteractions(reservations);
    }

    @Test
    void overdueLoanIsRejectedBeforeQueueCheck() {
        when(loans.findRenewalCandidateForReader(ITEM_ID, READER_ID)).thenReturn(Optional.of(
                new LoanRepository.RenewalCandidate(OffsetDateTime.parse("2026-10-07T09:00:00Z"), null)));
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM_ID, READER_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOAN_OVERDUE"));
        verifyNoInteractions(reservations);
    }

    @Test
    void missingOrForeignLoanIsNotInspectedForReservations() {
        when(loans.findRenewalCandidateForReader(ITEM_ID, READER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM_ID, READER_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        verifyNoInteractions(reservations);
    }
}
