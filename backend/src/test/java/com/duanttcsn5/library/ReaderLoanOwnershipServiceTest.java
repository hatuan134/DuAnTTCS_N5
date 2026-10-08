package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReaderLoanOwnershipServiceTest {
    LoanRepository loans = mock(LoanRepository.class);
    UserRepository users = mock(UserRepository.class);
    LoanService service;
    User actor;
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");
    @BeforeEach void setup() {
        service = new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans,
                mock(LibraryConfigurationService.class), Clock.fixed(AT.toInstant(), ZoneOffset.UTC));
        actor = new User(); actor.setId(12L); actor.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("READER"); actor.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(actor));
    }
    LoanDetailResponse header(Long readerId) {
        return new LoanDetailResponse(81L, "PM-81", null, readerId, "Bạn đọc", 3L, "Thủ thư", AT, List.of());
    }
    @Test void ownHeaderIsScopedBeforeItemsAreLoaded() {
        when(loans.findHeaderForReader(81L, 12L)).thenReturn(Optional.of(header(12L)));
        var item = new LoanDetailResponse.Item(91L, 31L, "LIB-31", 7L, "Mắt biếc", AT, AT.plusDays(14));
        when(loans.findItemsForStaff(81L)).thenReturn(List.of(item));
        var result = service.loanDetail(81L, 12L);
        assertThat(result.readerId()).isEqualTo(12L); assertThat(result.items()).containsExactly(item);
        var order = inOrder(loans);
        order.verify(loans).findHeaderForReader(81L, 12L);
        order.verify(loans).findItemsForStaff(81L);
        verify(loans, never()).findHeaderForStaff(anyLong());
    }
    @Test void foreignAndMissingLoansUseSameSafeErrorWithoutReadingItems() {
        for (long id : new long[]{82L, 999999L}) {
            when(loans.findHeaderForReader(id, 12L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.loanDetail(id, 12L)).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus().value()).isEqualTo(404);
                assertThat(e.getCode()).isEqualTo("LOAN_NOT_FOUND");
                assertThat(e.getMessage()).isEqualTo("Phiếu không tồn tại hoặc bạn không có quyền truy cập.");
                assertThat(e.getDetails()).isEmpty();
            });
        }
        verify(loans, never()).findHeaderForStaff(anyLong());
        verify(loans, never()).findItemsForStaff(anyLong());
    }
    @Test void staffRetainExistingAccessAndMissingLoanMessage() {
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            actor.getRole().setCode(role);
            when(loans.findHeaderForStaff(81L)).thenReturn(Optional.of(header(99L)));
            when(loans.findItemsForStaff(81L)).thenReturn(List.of());
            assertThat(service.loanDetail(81L, 12L).readerId()).isEqualTo(99L);
        }
        when(loans.findHeaderForStaff(82L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loanDetail(82L, 12L)).hasMessage("Không tìm thấy phiếu mượn.");
        verify(loans, never()).findHeaderForReader(anyLong(), anyLong());
    }
    @Test void invalidIdsAndUnauthenticatedInactiveUnknownRolesStopBeforeLoanQueries() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> service.loanDetail(id, 12L)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(400));
        }
        assertThatThrownBy(() -> service.loanDetail(81L, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.loanDetail(81L, 99L)).isInstanceOf(ApiException.class);
        actor.setStatus("LOCKED");
        assertThatThrownBy(() -> service.loanDetail(81L, 12L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(403));
        actor.setStatus("ACTIVE"); actor.getRole().setCode("UNKNOWN");
        assertThatThrownBy(() -> service.loanDetail(81L, 12L)).isInstanceOf(ApiException.class);
        actor.setRole(null);
        assertThatThrownBy(() -> service.loanDetail(81L, 12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(loans);
    }
}
