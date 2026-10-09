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

class ReaderLoanHistoryExportServiceTest {
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
    void exportUsesFullSnapshotOrFilteredSnapshotAndNeverAnotherReader() {
        when(loans.findReaderHistory(20L)).thenReturn(List.of(
                row(4, 40L, "2026-10-03T00:00:00+07:00", "2026-10-04T12:00:00+07:00"),
                row(3, 30L, "2026-10-10T00:00:00+07:00", null),
                row(2, 20L, "2026-10-03T00:00:00+07:00", "2026-10-03T12:00:00+07:00")));
        var all = service.exportReaderLoanHistory(20L, null, null);
        assertThat(all.rowCount()).isEqualTo(3);
        String csv = new String(all.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv.indexOf("PM-4")).isLessThan(csv.indexOf("PM-3"));
        assertThat(csv.indexOf("PM-3")).isLessThan(csv.indexOf("PM-2"));
        var filtered = service.exportReaderLoanHistory(20L, "2026-10-03", "2026-10-03");
        assertThat(filtered.rowCount()).isEqualTo(1);
        assertThat(new String(filtered.content(), java.nio.charset.StandardCharsets.UTF_8)).contains("PM-3").doesNotContain("PM-4", "PM-2");
        verify(loans, times(2)).findReaderHistory(20L);
        verifyNoMoreInteractions(loans);
    }

    @Test
    void invalidFilterAndReaderReuseBackendValidation() {
        assertThatThrownBy(() -> service.exportReaderLoanHistory(20L, "2026-02-30", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.exportReaderLoanHistory(20L, "2026-10-09", "2026-10-01")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.exportReaderLoanHistory(0L, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.exportReaderLoanHistory(999L, null, null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(loans);
    }
}
