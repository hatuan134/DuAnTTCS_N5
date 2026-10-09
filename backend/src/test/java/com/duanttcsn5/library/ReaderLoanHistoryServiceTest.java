package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.ReaderRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReaderLoanHistoryServiceTest {
    private final ReaderProfileRepository profiles = mock(ReaderProfileRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final LibraryCardRepository cards = mock(LibraryCardRepository.class);
    private final ReaderRegistrationService service = new ReaderRegistrationService(
            mock(UserRepository.class), profiles, mock(RoleRepository.class),
            mock(PasswordEncoder.class), mock(AuditLogRepository.class), cards, loans);

    @BeforeEach
    void setup() {
        User user = new User(); user.setId(20L); user.setFullName("Nguyễn An"); user.setEmail("an@example.invalid");
        ReaderProfile profile = new ReaderProfile(); profile.setUserId(20L); profile.setUser(user);
        profile.setMemberCode("BD000020"); profile.setDateOfBirth(LocalDate.of(2005, 1, 1));
        when(profiles.findById(20L)).thenReturn(Optional.of(profile));
        when(cards.findByUserIdWithDetails(20L)).thenReturn(Optional.empty());
    }

    private LoanRepository.ReaderHistoryRow row(long loan, Long item, String due, String returned) {
        return new LoanRepository.ReaderHistoryRow(loan, "PM-" + loan,
                OffsetDateTime.parse("2026-10-0" + loan + "T09:00:00+07:00"), item, "Sách " + item,
                "BC-" + item, OffsetDateTime.parse("2026-10-01T09:00:00+07:00"),
                due == null ? null : OffsetDateTime.parse(due),
                returned == null ? null : OffsetDateTime.parse(returned));
    }

    @Test
    void readerWithoutHistoryHasZeroCountsAndEmptyList() {
        when(loans.findReaderHistory(20L)).thenReturn(List.of());
        var result = service.getReaderLoanHistory(20L);
        assertThat(result.profile().userId()).isEqualTo(20L);
        assertThat(result.openLoanCount()).isZero();
        assertThat(result.totalBorrowCount()).isZero();
        assertThat(result.lateReturnCount()).isZero();
        assertThat(result.loans()).isEmpty();
    }

    @Test
    void countsLoansOnceAndPartialReturnsRemainOpenWithNewestLoanFirst() {
        when(loans.findReaderHistory(20L)).thenReturn(List.of(
                row(4, 41L, "2026-10-07T09:00:00+07:00", null),
                row(4, 42L, "2026-10-07T09:00:00+07:00", "2026-10-08T09:00:00+07:00"),
                row(3, 31L, "2026-10-07T09:00:00+07:00", "2026-10-08T09:00:00+07:00"),
                row(3, 32L, "2026-10-07T09:00:00+07:00", "2026-10-09T09:00:00+07:00"),
                row(2, 21L, "2026-10-07T09:00:00+07:00", "2026-10-07T23:59:00+07:00"),
                row(1, 11L, "2026-10-01T09:00:00+07:00", null)));
        var result = service.getReaderLoanHistory(20L);
        assertThat(result.openLoanCount()).isEqualTo(2);
        assertThat(result.totalBorrowCount()).isEqualTo(4);
        assertThat(result.lateReturnCount()).isEqualTo(2);
        assertThat(result.loans()).extracting(loan -> loan.id()).containsExactly(4L, 3L, 2L, 1L);
        assertThat(result.loans().get(0).status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(result.loans().get(0).returnedLate()).isTrue();
        assertThat(result.loans().get(1).items()).hasSize(2).allMatch(item -> item.returnedLate());
        assertThat(result.loans().get(2).returnedLate()).isFalse();
        assertThat(result.loans().get(3).returnedLate()).isFalse(); // overdue but not returned
    }

    @Test
    void comparesVietnamDatesAcrossUtcMidnightAndIgnoresMissingDueDate() {
        when(loans.findReaderHistory(20L)).thenReturn(List.of(
                row(3, 31L, "2026-10-07T17:00:00Z", "2026-10-08T16:59:59Z"), // same Vietnam day
                row(2, 21L, "2026-10-07T17:00:00Z", "2026-10-08T17:00:00Z"), // next Vietnam day
                row(1, 11L, null, "2026-10-09T09:00:00+07:00")));
        var result = service.getReaderLoanHistory(20L);
        assertThat(result.openLoanCount()).isZero();
        assertThat(result.totalBorrowCount()).isEqualTo(3);
        assertThat(result.lateReturnCount()).isEqualTo(1);
        assertThat(result.loans()).extracting(loan -> loan.returnedLate()).containsExactly(false, true, false);
        assertThat(result.loans().get(2).items().get(0).dueAt()).isNull();
    }

    @Test
    void legacyLoanWithoutItemsRemainsVisibleButIsNotOpenOrLate() {
        when(loans.findReaderHistory(20L)).thenReturn(List.of(row(1, null, null, null)));
        var result = service.getReaderLoanHistory(20L);
        assertThat(result.totalBorrowCount()).isEqualTo(1);
        assertThat(result.openLoanCount()).isZero();
        assertThat(result.lateReturnCount()).isZero();
        assertThat(result.loans().get(0).status()).isEqualTo("EMPTY");
        assertThat(result.loans().get(0).items()).isEmpty();
    }

    @Test
    void validatesIdAndReaderExistenceBeforeQueryingLendingData() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.getReaderLoanHistory(id)).isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_READER_ID"));
        }
        when(profiles.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReaderLoanHistory(999L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("READER_NOT_FOUND"));
        verifyNoInteractions(loans);
    }
}
