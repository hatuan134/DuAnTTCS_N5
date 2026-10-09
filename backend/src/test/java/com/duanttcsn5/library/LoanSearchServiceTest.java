package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.LoanSearchPageResponse;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        return rowAt(loanId, loanNumber, itemId, barcode, returnedDate, borrowed);
    }

    private LoanRepository.LoanSearchRow rowAt(long loanId, String loanNumber, long itemId,
                                               String barcode, String returnedDate,
                                               OffsetDateTime borrowedAt) {
        return new LoanRepository.LoanSearchRow(loanId, loanNumber, "THE-01", "Nguyễn An",
                borrowedAt, itemId, barcode, "Mắt biếc", borrowedAt.plusDays(14),
                returnedDate == null ? null : OffsetDateTime.parse(returnedDate));
    }

    @Test
    void overlappingCodesGroupItemsByLoanNotByBarcode() {
        var first = row(2L, "THE-01", 11L, "THE-01", null);
        var second = row(2L, "THE-01", 12L, "COPY-02", "2026-10-04T08:00:00+07:00");
        when(loans.countLoansByCode("THE-01")).thenReturn(1L);
        when(loans.searchLoansByCode("THE-01", 20, 0L)).thenReturn(List.of(first, second));
        LoanSearchPageResponse result = service.searchLoans("  THE-01  ", 0, 12L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items().get(0).id()).isEqualTo(2L);
        assertThat(result.items().get(0).items()).hasSize(2);
        assertThat(result.items().get(0).status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(result.items().get(0).cardNumber()).isEqualTo("THE-01");
        assertThat(result.items().get(0).items().get(0).dueAt()).isEqualTo(borrowed.plusDays(14));
        verify(loans).searchLoansByCode("THE-01", 20, 0L);
    }

    @Test
    void returnedAndOpenLoansKeepRepositoryOrderAndHistory() {
        var open = row(2L, "PM-02", 11L, "BAR-99", null);
        var closed = row(1L, "PM-01", 10L, "BAR-99", "2026-10-03T08:00:00+07:00");
        when(loans.countLoansByCode("BAR-99")).thenReturn(2L);
        when(loans.searchLoansByCode("BAR-99", 20, 0L)).thenReturn(List.of(open, closed));
        var result = service.searchLoans("BAR-99", 0, 12L);
        assertThat(result.items()).extracting(r -> r.loanNumber()).containsExactly("PM-02", "PM-01");
        assertThat(result.items()).extracting(r -> r.status()).containsExactly("BORROWED", "RETURNED");
        assertThat(result.items().get(1).items().get(0).status()).isEqualTo("RETURNED");
    }

    @Test
    void zeroNineteenTwentyAndTwentyOneRespectBoundaries() {
        for (int count : new int[]{0, 19, 20, 21, 47}) {
            reset(loans);
            when(loans.countLoansByCode("THE-01")).thenReturn((long) count);
            // Repository pages already carry the SQL's stable priority + date + id order.
            List<LoanRepository.LoanSearchRow> ordered = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ordered.add(rowAt(1000L + i, "PM-" + i, i + 1,
                        "COPY-" + i, i < count / 2 ? null : "2026-10-03T08:00:00+07:00",
                        borrowed.minusMinutes(i)));
            }
            when(loans.searchLoansByCode(eq("THE-01"), eq(20), anyLong())).thenAnswer(call -> {
                long offset = call.getArgument(2);
                if (offset >= ordered.size()) return List.of();
                return ordered.subList((int) offset, Math.min((int) offset + 20, ordered.size()));
            });
            Set<Long> seen = new HashSet<>();
            for (int page = 0; page < Math.max(1, (count + 19) / 20); page++) {
                var result = service.searchLoans("THE-01", page, 12L);
                assertThat(result.total()).isEqualTo(count);
                assertThat(result.page()).isEqualTo(page);
                assertThat(result.items()).hasSize(Math.min(20, Math.max(0, count - page * 20)));
                for (var loan : result.items()) {
                    assertThat(seen.add(loan.id())).as("loan never repeats between pages").isTrue();
                }
            }
            assertThat(seen).hasSize(count);
            var beyondLast = service.searchLoans("THE-01", (count + 19) / 20 + 1, 12L);
            assertThat(beyondLast.items()).isEmpty();
        }
    }

    @Test
    void invalidCodeAndPageAreRejectedBeforeRepositoryAccess() {
        for (String input : new String[]{null, "", "    ", "X".repeat(101)}) {
            assertThatThrownBy(() -> service.searchLoans(input, 0, 12L))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(400);
                        assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_CODE");
                    });
        }
        assertThatThrownBy(() -> service.searchLoans("PM-01", -1, 12L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_PAGE");
                });
        verifyNoInteractions(loans);
    }

    @Test
    void onlyActiveStaffCanSearch() {
        assertThatThrownBy(() -> service.searchLoans("PM-01", 0, null)).isInstanceOf(ApiException.class);
        actor.getRole().setCode("READER");
        assertThatThrownBy(() -> service.searchLoans("PM-01", 0, 12L)).isInstanceOf(ApiException.class);
        actor.getRole().setCode("LIBRARIAN");
        actor.setStatus("LOCKED");
        assertThatThrownBy(() -> service.searchLoans("PM-01", 0, 12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(loans);
    }

    @Test
    void existingManagerAndAdminRolesAlsoWork() {
        for (String role : List.of("LIBRARY_MANAGER", "ADMIN")) {
            actor.getRole().setCode(role);
            when(loans.countLoansByCode("PM-01")).thenReturn(0L);
            assertThat(service.searchLoans("PM-01", 0, 12L).items()).isEmpty();
        }
        verify(loans, times(2)).countLoansByCode("PM-01");
        verify(loans, never()).searchLoansByCode(anyString(), anyInt(), anyLong());
    }

    @Test
    void fromDateOnlyFiltersBeforeCountingAndPaginates() {
        var from = java.time.LocalDate.parse("2026-10-01");
        when(loans.countLoansByCode("THE-01", from, null, null)).thenReturn(21L);
        when(loans.searchLoansByCode("THE-01", from, null, null, 20, 20L))
                .thenReturn(List.of(row(2L, "PM-02", 11L, "BC-11", null)));
        var response = service.searchLoans("THE-01", 1, 12L, "2026-10-01", null, "ALL");
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        verify(loans).searchLoansByCode("THE-01", from, null, null, 20, 20L);
    }

    @Test
    void endDateOnlyAndCombinedStatusUseOneSharedFilterForCountAndRows() {
        var from = java.time.LocalDate.parse("2026-09-01");
        var to = java.time.LocalDate.parse("2026-10-01");
        when(loans.countLoansByCode("THE-01", null, to, null)).thenReturn(0L);
        assertThat(service.searchLoans("THE-01", 0, 12L, null, "2026-10-01", null).total())
                .isZero();
        verify(loans, never()).searchLoansByCode(eq("THE-01"), any(), any(), any(), anyInt(), anyLong());
        when(loans.countLoansByCode("THE-01", from, to, "PARTIALLY_RETURNED"))
                .thenReturn(1L);
        when(loans.searchLoansByCode("THE-01", from, to, "PARTIALLY_RETURNED", 20, 0L))
                .thenReturn(List.of(row(2L, "PM-02", 11L, "BC-11", null),
                        row(2L, "PM-02", 12L, "BC-12", "2026-10-01T12:00:00+07:00")));
        var response = service.searchLoans(" THE-01 ", 0, 12L,
                "2026-09-01", "2026-10-01", "PARTIALLY_RETURNED");
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(response.items().get(0).items()).hasSize(2);
        verify(loans).searchLoansByCode("THE-01", from, to, "PARTIALLY_RETURNED", 20, 0L);
    }

    @Test
    void everySupportedStatusIncludingEmptyIsAccepted() {
        for (String status : List.of("BORROWED", "RETURNED", "PARTIALLY_RETURNED", "EMPTY")) {
            when(loans.countLoansByCode("PM-01", null, null, status)).thenReturn(0L);
            var result = service.searchLoans("PM-01", 0, 12L, null, null, status);
            assertThat(result.total()).isZero();
            verify(loans).countLoansByCode("PM-01", null, null, status);
        }
    }

    @Test
    void rejectsInvertedInvalidOrUnknownFiltersWithoutDatabaseReads() {
        assertThatThrownBy(() -> service.searchLoans("PM-01", 0, 12L,
                "2026-10-09", "2026-10-01", null))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_DATE_RANGE"));
        for (String bad : List.of("09/10/2026", "2026-02-30", "2026-13-01", "0000-01-01")) {
            assertThatThrownBy(() -> service.searchLoans("PM-01", 0, 12L, bad, null, null))
                    .isInstanceOfSatisfying(ApiException.class, e ->
                            assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_DATE"));
        }
        assertThatThrownBy(() -> service.searchLoans("PM-01", 0, 12L, null, null, "LOST"))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo("INVALID_LOAN_SEARCH_STATUS"));
        verifyNoInteractions(loans);
    }
}
