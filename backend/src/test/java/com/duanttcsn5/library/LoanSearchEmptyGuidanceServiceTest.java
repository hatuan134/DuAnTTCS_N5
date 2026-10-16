package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoanSearchEmptyGuidanceServiceTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final BookCopyRepository copies = mock(BookCopyRepository.class);
    private final LibraryCardRepository cards = mock(LibraryCardRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LoanService service = new LoanService(
            mock(BookRepository.class), mock(BookReservationRepository.class), copies, cards,
            users, loans, mock(LibraryConfigurationService.class), Clock.systemUTC());

    @BeforeEach
    void librarian() {
        var user = new User();
        user.setId(9L);
        user.setStatus("ACTIVE");
        var role = new Role();
        role.setCode("LIBRARIAN");
        user.setRole(role);
        when(users.findById(9L)).thenReturn(Optional.of(user));
    }

    @Test
    void completelyUnknownCodeAndOneCharacterTypoReturnCodeNotFound() {
        for (String code : new String[]{"MISSING-12345", "TV-001X"}) {
            var result = service.searchLoans(" " + code + " ", 0, 9L);
            assertThat(result.items()).isEmpty();
            assertThat(result.total()).isZero();
            assertThat(result.emptyReason()).isEqualTo("CODE_NOT_FOUND");
        }
    }

    @Test
    void existingCardWithNoLoansHasDistinctReason() {
        when(cards.existsByCardNumber("TV-0002")).thenReturn(true);
        var result = service.searchLoans("TV-0002", 0, 9L);
        assertThat(result.emptyReason()).isEqualTo("NO_LOANS_FOR_CODE");
        verify(cards).existsByCardNumber("TV-0002");
    }

    @Test
    void existingCopyWithoutHistoryHasDistinctReason() {
        when(copies.existsByBarcode("BAR-0002")).thenReturn(true);
        var result = service.searchLoans("BAR-0002", 0, 9L);
        assertThat(result.emptyReason()).isEqualTo("NO_LOANS_FOR_CODE");
    }

    @Test
    void existingLoanNumberFilteredOutHasDifferentReason() {
        var fromDate = LocalDate.of(2026, 10, 1);
        when(loans.countLoansByCode("PM-001", fromDate, null, null)).thenReturn(0L);
        when(loans.countLoansByCode("PM-001")).thenReturn(1L);
        var result = service.searchLoans("PM-001", 0, 9L, "2026-10-01", null, null);
        assertThat(result.emptyReason()).isEqualTo("NO_LOANS_MATCH_FILTERS");
        verify(loans, never()).existsByLoanNumber("PM-001");
        verifyNoInteractions(cards, copies);
    }

    @Test
    void aLoanNumberCanExistEvenWithoutMatchingSearchRows() {
        when(loans.existsByLoanNumber("PM-OLD")).thenReturn(true);
        assertThat(service.searchLoans("PM-OLD", 0, 9L).emptyReason())
                .isEqualTo("NO_LOANS_FOR_CODE");
    }

    @Test
    void emptyPageBeyondLastDoesNotClaimThatTheCodeIsMissing() {
        when(loans.countLoansByCode("PM-001")).thenReturn(2L);
        var result = service.searchLoans("PM-001", 1, 9L);
        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.items()).isEmpty();
        assertThat(result.emptyReason()).isNull();
        verify(loans, never()).existsByLoanNumber(anyString());
    }
}
