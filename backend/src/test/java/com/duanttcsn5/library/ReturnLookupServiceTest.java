package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnLookupServiceTest {
    LoanRepository loans = mock(LoanRepository.class);
    UserRepository users = mock(UserRepository.class);
    LibraryConfigurationService calendar = mock(LibraryConfigurationService.class);
    User staff;
    LoanService service;

    @BeforeEach void setup() {
        service = serviceAt("2026-10-08T18:00:00Z"); // 01:00 on Oct 9 in Vietnam
        staff = new User(); staff.setId(12L); staff.setStatus("ACTIVE");
        Role role = new Role(); role.setCode("LIBRARIAN"); staff.setRole(role);
        when(users.findById(12L)).thenReturn(Optional.of(staff));
    }
    LoanService serviceAt(String instant) {
        return new LoanService(mock(BookRepository.class), mock(BookReservationRepository.class),
                mock(BookCopyRepository.class), mock(LibraryCardRepository.class), users, loans, calendar,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
    LoanRepository.ReturnLookupRow row(String due) {
        return new LoanRepository.ReturnLookupRow(4L, "LIB-001", "Mắt biếc", 8L, "PM-008", 9L,
                20L, "Nguyễn An", OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                due == null ? null : OffsetDateTime.parse(due));
    }
    void given(String due) { when(loans.findReturnLookup("LIB-001")).thenReturn(Optional.of(row(due))); }

    @Test void exactBorrowerAndItemFieldsAreKeptAndBarcodeIsTrimmed() {
        given("2026-10-10T10:00:00Z");
        var actual = service.lookupReturn("  LIB-001  ", 12L);
        assertThat(actual.readerName()).isEqualTo("Nguyễn An");
        assertThat(actual.readerId()).isEqualTo(20L);
        assertThat(actual.bookTitle()).isEqualTo("Mắt biếc");
        assertThat(actual.loanId()).isEqualTo(8L);
        assertThat(actual.itemId()).isEqualTo(9L);
        assertThat(actual.barcode()).isEqualTo("LIB-001");
        assertThat(actual.borrowedAt()).isEqualTo(row(null).borrowedAt());
        assertThat(actual.dueAt()).isEqualTo(row("2026-10-10T10:00:00Z").dueAt());
        assertThat(actual.status()).isEqualTo("ON_TIME");
        assertThat(actual.overdueDays()).isZero();
        assertThat(actual.checkedOn()).isEqualTo(LocalDate.of(2026, 10, 9));
        verify(loans).findReturnLookup("LIB-001"); verifyNoMoreInteractions(loans);
    }
    @Test void deadlineTodayRemainsOnTimeEvenAfterDeadlineHour() {
        service = serviceAt("2026-10-09T16:59:59Z");
        given("2026-10-09T03:00:00Z");
        var actual = service.lookupReturn("LIB-001", 12L);
        assertThat(actual.overdueDays()).isZero(); assertThat(actual.status()).isEqualTo("ON_TIME");
    }
    @Test void overdueUsesVietnamDateNotUtcOrHours() {
        given("2026-10-08T10:00:00Z");
        var actual = service.lookupReturn("LIB-001", 12L);
        assertThat(actual.overdueDays()).isEqualTo(1L); assertThat(actual.status()).isEqualTo("OVERDUE");
    }
    @Test void deadlineOffsetIsConvertedToVietnamBeforeCounting() {
        given("2026-10-08T18:00:00Z");
        assertThat(service.lookupReturn("LIB-001", 12L).overdueDays()).isZero();
    }
    @Test void closedDaysIncludingSundayAndClosedLookupDateCountWithoutMovingDeadline() {
        service = serviceAt("2026-10-11T18:00:00Z"); // Monday Vietnam, weekend included
        given("2026-10-09T10:00:00Z");
        assertThat(service.lookupReturn("LIB-001", 12L).overdueDays()).isEqualTo(3L);
        service = serviceAt("2026-10-11T03:00:00Z"); // Sunday itself
        assertThat(service.lookupReturn("LIB-001", 12L).overdueDays()).isEqualTo(2L);
        given("2026-10-11T10:00:00Z"); // historical deadline on a closed day
        service = serviceAt("2026-10-12T03:00:00Z");
        assertThat(service.lookupReturn("LIB-001", 12L).overdueDays()).isEqualTo(1L);
        verifyNoInteractions(calendar);
    }
    @Test void existingCopyWithoutOpenItemIsNotMissingBarcode() {
        when(loans.findReturnLookup("LIB-001")).thenReturn(Optional.of(new LoanRepository.ReturnLookupRow(
                4L, "LIB-001", "Mắt biếc", null, null, null, null, null, null, null)));
        var actual = service.lookupReturn("LIB-001", 12L);
        assertThat(actual.status()).isEqualTo("NOT_BORROWED");
        assertThat(actual.message()).isEqualTo("Bản sao này hiện không có ai mượn.");
        assertThat(actual.readerName()).isNull(); assertThat(actual.overdueDays()).isNull();
    }
    @Test void missingBarcodeHasSeparate404Code() {
        when(loans.findReturnLookup("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lookupReturn("UNKNOWN", 12L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(404);
            assertThat(e.getCode()).isEqualTo("COPY_BARCODE_NOT_FOUND");
        });
    }
    @Test void legacyMissingDeadlineIsNeverReportedOnTime() {
        given(null); var actual = service.lookupReturn("LIB-001", 12L);
        assertThat(actual.status()).isEqualTo("MISSING_DUE_DATE");
        assertThat(actual.dueAt()).isNull(); assertThat(actual.overdueDays()).isNull();
    }
    @Test void validatesEmptyWhitespaceAndLengthBeforeQuerying() {
        for (String barcode : new String[]{null, "", "  ", "X".repeat(101)}) {
            assertThatThrownBy(() -> service.lookupReturn(barcode, 12L)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getCode()).isEqualTo("INVALID_BARCODE"));
        }
        verifyNoInteractions(loans);
    }
    @Test void readerInactiveMissingAndAnonymousNeverQueryBorrowerData() {
        assertThatThrownBy(() -> service.lookupReturn("LIB-001", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.lookupReturn("LIB-001", 99L)).isInstanceOf(ApiException.class);
        staff.setStatus("LOCKED");
        assertThatThrownBy(() -> service.lookupReturn("LIB-001", 12L)).isInstanceOf(ApiException.class);
        staff.setStatus("ACTIVE"); staff.getRole().setCode("READER");
        assertThatThrownBy(() -> service.lookupReturn("LIB-001", 12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(loans);
    }
    @Test void allExistingStaffRolesCanLookup() {
        given("2026-10-10T10:00:00Z");
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            staff.getRole().setCode(role);
            assertThat(service.lookupReturn("LIB-001", 12L).status()).isEqualTo("ON_TIME");
        }
    }
}
