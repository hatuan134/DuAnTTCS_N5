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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OverdueLoansServiceTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LibraryConfigurationService calendar = mock(LibraryConfigurationService.class);
    private final User staff = new User();
    private LoanService service;

    @BeforeEach
    void setup() {
        staff.setId(12L);
        staff.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("LIBRARIAN");
        staff.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(staff));
        service = serviceAt("2026-10-08T18:00:00Z"); // 01:00 ngày 09/10 tại Việt Nam
    }

    private LoanService serviceAt(String instant) {
        return new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans, calendar,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private LoanRepository.OverdueLoanRow row(long loanId, long itemId, String loanNumber,
                                               String readerName, String phone, String bookTitle,
                                               String dueAt) {
        return new LoanRepository.OverdueLoanRow(loanId, loanNumber, itemId, 20L, readerName, phone,
                30L + itemId, bookTitle, OffsetDateTime.parse(dueAt));
    }

    @Test
    void noOverdueItemsReturnsEmptyList() {
        OffsetDateTime todayStart = OffsetDateTime.parse("2026-10-09T00:00:00+07:00");
        when(loans.findOpenOverdue(todayStart)).thenReturn(List.of());

        assertThat(service.overdueLoans(12L)).isEmpty();
        verify(loans).findOpenOverdue(todayStart);
        verifyNoInteractions(calendar);
    }

    @Test
    void calculatesCalendarDaysKeepsContactAndBookFieldsAndSortsLongestDelayFirst() {
        OffsetDateTime todayStart = OffsetDateTime.parse("2026-10-09T00:00:00+07:00");
        when(loans.findOpenOverdue(todayStart)).thenReturn(List.of(
                row(4L, 44L, "PM-004", "Bạn đọc D", "0904000004", "Sách D", "2026-10-08T10:00:00+07:00"),
                row(2L, 22L, "PM-002", "Bạn đọc B", "0902000002", "Sách B", "2026-10-07T15:00:00+07:00"),
                row(1L, 11L, "PM-001", "Bạn đọc A", "0901000001", "Sách A", "2026-10-05T09:00:00+07:00"),
                row(3L, 33L, "PM-003", "Bạn đọc C", null, "Sách C", "2026-10-07T08:00:00+07:00")
        ));

        var actual = service.overdueLoans(12L);

        assertThat(actual).extracting(item -> item.loanNumber())
                .containsExactly("PM-001", "PM-003", "PM-002", "PM-004");
        assertThat(actual).extracting(item -> item.overdueDays())
                .containsExactly(4L, 2L, 2L, 1L);
        assertThat(actual.get(0).readerName()).isEqualTo("Bạn đọc A");
        assertThat(actual.get(0).readerPhone()).isEqualTo("0901000001");
        assertThat(actual.get(0).bookTitle()).isEqualTo("Sách A");
        assertThat(actual.get(1).readerPhone()).isNull();
        verifyNoInteractions(calendar);
    }

    @Test
    void closedDaysRemainPartOfOverdueDayCount() {
        service = serviceAt("2026-10-11T18:00:00Z"); // 01:00 thứ Hai 12/10 tại Việt Nam
        OffsetDateTime todayStart = OffsetDateTime.parse("2026-10-12T00:00:00+07:00");
        when(loans.findOpenOverdue(todayStart)).thenReturn(List.of(
                row(1L, 11L, "PM-001", "Bạn đọc A", "0901000001", "Sách A", "2026-10-09T10:00:00+07:00")
        ));

        assertThat(service.overdueLoans(12L).get(0).overdueDays()).isEqualTo(3L);
        verifyNoInteractions(calendar);
    }

    @Test
    void anonymousInactiveAndReaderCannotReadOverdueList() {
        assertThatThrownBy(() -> service.overdueLoans(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.overdueLoans(99L)).isInstanceOf(ApiException.class);

        staff.setStatus("LOCKED");
        assertThatThrownBy(() -> service.overdueLoans(12L)).isInstanceOf(ApiException.class);

        staff.setStatus("ACTIVE");
        staff.getRole().setCode("READER");
        assertThatThrownBy(() -> service.overdueLoans(12L)).isInstanceOf(ApiException.class);

        verifyNoInteractions(loans);
    }

    @Test
    void allExistingStaffRolesCanReadOverdueList() {
        OffsetDateTime todayStart = OffsetDateTime.parse("2026-10-09T00:00:00+07:00");
        when(loans.findOpenOverdue(todayStart)).thenReturn(List.of());

        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            staff.getRole().setCode(role);
            assertThat(service.overdueLoans(12L)).isEmpty();
        }
    }
}
