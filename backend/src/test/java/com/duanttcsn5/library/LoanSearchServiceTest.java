package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.LoanSearchResultResponse;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanSearchServiceTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LoanService service = new LoanService(
            mock(BookRepository.class), mock(BookReservationRepository.class),
            mock(BookCopyRepository.class), mock(LibraryCardRepository.class),
            users, loans, mock(LibraryConfigurationService.class), Clock.systemUTC());
    private final User actor = new User();
    private final OffsetDateTime borrowed = OffsetDateTime.parse("2026-10-01T08:00:00+07:00");

    @BeforeEach
    void staff() {
        actor.setId(12L);
        actor.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("LIBRARIAN");
        actor.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(actor));
    }

    private LoanRepository.LoanSearchRow row(long loanId, String loanNumber, long itemId,
                                             String barcode, String returnedDate) {
        return new LoanRepository.LoanSearchRow(loanId, loanNumber, "THE-01", "Nguyễn An",
                borrowed, itemId, barcode, "Mắt biếc", borrowed.plusDays(14),
                returnedDate == null ? null : OffsetDateTime.parse(returnedDate));
    }

    @Test
    void oneCodeMatchesLoanCardAndBarcodeWithoutDuplicateLoans() {
        var first = row(2L, "THE-01", 11L, "THE-01", null);
        var second = row(2L, "THE-01", 12L, "COPY-02", "2026-10-04T08:00:00+07:00");
        when(loans.searchLoansByCode("THE-01")).thenReturn(List.of(first, second));
        List<LoanSearchResultResponse> result = service.searchLoans("  THE-01  ", 12L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(0).items()).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(result.get(0).cardNumber()).isEqualTo("THE-01");
        assertThat(result.get(0).items().get(0).dueAt()).isEqualTo(borrowed.plusDays(14));
        verify(loans).searchLoansByCode("THE-01");
    }

    @Test
    void historicalBarcodeFindsAllRelatedLoansEvenReturned() {
        var older = row(1L, "PM-01", 10L, "BAR-99", "2026-10-03T08:00:00+07:00");
        var newer = row(2L, "PM-02", 11L, "BAR-99", null);
        when(loans.searchLoansByCode("BAR-99")).thenReturn(List.of(newer, older));
        var result = service.searchLoans("BAR-99", 12L);
        assertThat(result).extracting(LoanSearchResultResponse::loanNumber)
                .containsExactly("PM-02", "PM-01");
        assertThat(result).extracting(LoanSearchResultResponse::status)
                .containsExactly("BORROWED", "RETURNED");
    }

    @Test
    void returnsEmptyListIfCodeDoesNotMatch() {
        when(loans.searchLoansByCode("NOT-FOUND")).thenReturn(List.of());
        assertThat(service.searchLoans("NOT-FOUND", 12L)).isEmpty();
    }

    @Test
    void invalidCodeIsRejectedBeforeRepositoryAccess() {
        for (String input : new String[]{null, "", "    ", "X".repeat(101)}) {
            assertThatThrownBy(() -> service.searchLoans(input, 12L))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_CODE");
                    });
        }
        verifyNoInteractions(loans);
    }

    @Test
    void onlyActiveStaffCanSearch() {
        assertThatThrownBy(() -> service.searchLoans("PM-01", null)).isInstanceOf(ApiException.class);
        actor.getRole().setCode("READER");
        assertThatThrownBy(() -> service.searchLoans("PM-01", 12L)).isInstanceOf(ApiException.class);
        actor.getRole().setCode("LIBRARIAN");
        actor.setStatus("LOCKED");
        assertThatThrownBy(() -> service.searchLoans("PM-01", 12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(loans);
    }

    @Test
    void existingManagerAndAdminRolesAlsoWork() {
        for (String role : List.of("LIBRARY_MANAGER", "ADMIN")) {
            actor.getRole().setCode(role);
            when(loans.searchLoansByCode("PM-01")).thenReturn(List.of());
            assertThat(service.searchLoans("PM-01", 12L)).isEmpty();
        }
        verify(loans, times(2)).searchLoansByCode("PM-01");
    }
}
