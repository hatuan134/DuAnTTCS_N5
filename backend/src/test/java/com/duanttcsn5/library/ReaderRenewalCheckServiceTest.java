package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReaderRenewalCheckServiceTest {
    private final LoanRepository repository = mock(LoanRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private LoanService service;
    private User actor;

    private static final long ITEM = 100L;
    private static final long READER = 12L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T09:00:00Z");

    @BeforeEach
    void setup() {
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, repository,
                mock(LibraryConfigurationService.class), Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        actor = new User();
        actor.setId(READER);
        actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); actor.setRole(role);
        when(users.findById(READER)).thenReturn(Optional.of(actor));
        when(repository.findRenewalPolicyForReader(ITEM, READER)).thenReturn(
                Optional.of(new LoanRepository.RenewalPolicy(50L, 0, 3)));
        when(repository.incrementRenewalCountIfAllowed(50L, READER)).thenReturn(1);
    }

    private void candidate(OffsetDateTime dueAt, OffsetDateTime returnedAt) {
        when(repository.findRenewalCandidateForReader(ITEM, READER))
                .thenReturn(Optional.of(new LoanRepository.RenewalCandidate(dueAt, returnedAt)));
    }

    private void rejects(String code, int status) {
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, READER))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(code);
                    assertThat(exception.getStatus().value()).isEqualTo(status);
                });
    }

    @Test void openLoanDueTodayOrLaterRecordsAllowedRenewalWithoutChangingDueDate() {
        candidate(OffsetDateTime.parse("2026-10-08T02:00:00Z"), null);
        assertThat(service.checkMyLoanRenewal(ITEM, READER).eligible()).isTrue();
        assertThat(service.checkMyLoanRenewal(ITEM, READER).message()).contains("chưa thay đổi");
        candidate(OffsetDateTime.parse("2026-10-10T02:00:00Z"), null);
        assertThat(service.checkMyLoanRenewal(ITEM, READER).eligible()).isTrue();
        verify(repository, never()).insertItem(anyLong(), anyLong(), any());
    }

    @Test void returnedItemIsRejectedEvenWhenNotOverdue() {
        candidate(OffsetDateTime.parse("2026-10-15T02:00:00Z"), NOW.minusDays(1));
        rejects("LOAN_ALREADY_RETURNED", 409);
    }

    @Test void overdueItemIsRejected() {
        candidate(OffsetDateTime.parse("2026-10-07T16:59:00Z"), null);
        rejects("LOAN_OVERDUE", 409);
    }

    @Test void itemThatTurnedOverdueAtVietnameseMidnightIsRejectedOnRecheck() {
        // The UI displayed this on Oct 8. By confirmation, Vietnam is already on Oct 9.
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, repository,
                mock(LibraryConfigurationService.class), Clock.fixed(
                        OffsetDateTime.parse("2026-10-08T17:00:01Z").toInstant(), ZoneOffset.UTC));
        candidate(OffsetDateTime.parse("2026-10-08T09:00:00Z"), null);
        rejects("LOAN_OVERDUE", 409);
        verify(repository).findRenewalCandidateForReader(ITEM, READER);
    }

    @Test void dueDateMissingIsRejected() {
        candidate(null, null);
        rejects("LOAN_DUE_DATE_MISSING", 409);
    }

    @Test void foreignAndNonexistentItemsProduceTheSameSafe404() {
        when(repository.findRenewalCandidateForReader(ITEM, READER)).thenReturn(Optional.empty());
        rejects("LOAN_ITEM_NOT_FOUND", 404);
    }

    @Test void inactiveOrNonReaderAccountCannotCheckOrReadLoans() {
        actor.setStatus("LOCKED");
        rejects("READER_ROLE_REQUIRED", 403);
        actor.setStatus("ACTIVE"); actor.getRole().setCode("LIBRARIAN");
        rejects("READER_ROLE_REQUIRED", 403);
        verifyNoInteractions(repository);
    }

    @Test void invalidItemIdAndUnauthenticatedRequestsAreRejected() {
        assertThatThrownBy(() -> service.checkMyLoanRenewal(0L, READER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.checkMyLoanRenewal(ITEM, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(401));
        verifyNoInteractions(repository);
    }
}
