package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.CreateOverdueContactRequest;
import com.duanttcsn5.library.dto.loan.OverdueContactResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueContactRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.OverdueContactService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OverdueContactServiceTest {
    private final OverdueContactRepository contacts = mock(OverdueContactRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T10:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final OverdueContactService service = new OverdueContactService(contacts, users, clock);

    private void staff(long id, String name, String roleCode) {
        User user = new User();
        user.setId(id); user.setFullName(name); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
    }

    @Test void firstAndSecondContactPreserveBothHistoryEntriesAndStaffSnapshots() {
        staff(10, "Thủ thư A", "LIBRARIAN");
        staff(11, "Thủ thư B", "LIBRARIAN");
        when(contacts.lockOpenOverdueLoan(eq(5L), any())).thenReturn(true);
        OffsetDateTime recorded = OffsetDateTime.parse("2026-10-09T17:30:00+07:00");
        OverdueContactResponse first = new OverdueContactResponse(1L, 5L, 10L, "Thủ thư A", "Đã gọi điện", recorded);
        OverdueContactResponse second = new OverdueContactResponse(2L, 5L, 11L, "Thủ thư B", "Hẹn trả ngày mai", recorded.plusMinutes(3));
        when(contacts.insert(eq(5L), eq(10L), eq("Thủ thư A"), eq("Đã gọi điện"), any())).thenReturn(first);
        when(contacts.insert(eq(5L), eq(11L), eq("Thủ thư B"), eq("Hẹn trả ngày mai"), any())).thenReturn(second);
        assertThat(service.record(5L, new CreateOverdueContactRequest("  Đã gọi điện  "), 10L)).isEqualTo(first);
        var later = new OverdueContactService(contacts, users,
                Clock.fixed(Instant.parse("2026-10-09T10:33:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        assertThat(later.record(5L, new CreateOverdueContactRequest("Hẹn trả ngày mai"), 11L)).isEqualTo(second);
        verify(contacts).insert(5L, 10L, "Thủ thư A", "Đã gọi điện", recorded);
        verify(contacts).insert(5L, 11L, "Thủ thư B", "Hẹn trả ngày mai", recorded.plusMinutes(3));
        when(contacts.loanExists(5L)).thenReturn(true);
        when(contacts.history(5L)).thenReturn(List.of(second, first));
        assertThat(service.history(5L, 10L)).containsExactly(second, first);
    }

    @Test void aVoucherWithoutPreviousContactsReturnsEmptyHistory() {
        staff(10, "Thủ thư", "LIBRARIAN");
        when(contacts.loanExists(5L)).thenReturn(true);
        assertThat(service.history(5L, 10L)).isEmpty();
    }

    @Test void blankAndTooLongNotesAreRejectedBeforeAnyWrite() {
        staff(10, "Thủ thư", "LIBRARIAN");
        assertThatThrownBy(() -> service.record(5L, new CreateOverdueContactRequest("    "), 10L))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.record(5L, new CreateOverdueContactRequest("a".repeat(1001)), 10L))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(contacts);
    }

    @Test void managerAndReaderCannotRecordContacts() {
        staff(10, "Quản lý", "LIBRARY_MANAGER");
        staff(11, "Bạn đọc", "READER");
        assertThatThrownBy(() -> service.record(5L, new CreateOverdueContactRequest("Đã gọi"), 10L))
                .isInstanceOf(ApiException.class).satisfies(error ->
                    assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.history(5L, 11L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(contacts);
    }

    @Test void cannotCreateContactForLoanThatIsNoLongerOverdue() {
        staff(10, "Thủ thư", "LIBRARIAN");
        assertThatThrownBy(() -> service.record(5L, new CreateOverdueContactRequest("Đã gọi"), 10L))
                .isInstanceOf(ApiException.class).satisfies(error ->
                    assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(contacts, never()).insert(anyLong(), anyLong(), any(), any(), any());
    }

    @Test void historyDoesNotRequireTheVoucherToRemainOverdue() {
        staff(10, "Thủ thư", "LIBRARIAN");
        when(contacts.loanExists(5L)).thenReturn(true);
        when(contacts.history(5L)).thenReturn(List.of(new OverdueContactResponse(
                1L, 5L, 10L, "Thủ thư", "Nhắc trả", OffsetDateTime.now(clock))));
        assertThat(service.history(5L, 10L)).hasSize(1);
        verify(contacts, never()).lockOpenOverdueLoan(anyLong(), any());
    }

    @Test void anonymousOrInvalidVoucherAreRejected() {
        assertThatThrownBy(() -> service.history(5L, null)).isInstanceOf(ApiException.class);
        staff(10, "Thủ thư", "LIBRARIAN");
        assertThatThrownBy(() -> service.history(0L, 10L)).isInstanceOf(ApiException.class);
    }
}
