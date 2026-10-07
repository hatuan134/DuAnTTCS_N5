package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.LibraryClosedDate;
import com.duanttcsn5.library.entity.LibraryWeeklySchedule;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryConfigurationLoanDatesTest {
    private LibraryWeeklyScheduleRepository weekly;
    private LibraryClosedDateRepository closed;
    private LibraryConfigurationService service;

    @BeforeEach void setup() {
        weekly = mock(LibraryWeeklyScheduleRepository.class);
        closed = mock(LibraryClosedDateRepository.class);
        service = new LibraryConfigurationService(mock(WarehouseRepository.class), mock(ShelfRepository.class),
                weekly, closed, mock(AuditLogRepository.class));
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(schedule());
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of());
    }

    private List<LibraryWeeklySchedule> schedule() {
        List<LibraryWeeklySchedule> result = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            var row = new LibraryWeeklySchedule(); row.setDayOfWeek(day); row.setOpen(day != 7);
            if (row.isOpen()) { row.setOpenTime(LocalTime.of(8, 0)); row.setCloseTime(LocalTime.of(day == 6 ? 12 : 17, 0)); }
            result.add(row);
        }
        return result;
    }

    private LibraryClosedDate holiday(String date) {
        var row = new LibraryClosedDate(); row.setClosedDate(LocalDate.parse(date)); row.setReason("Kiểm thử S3-01.2");
        return row;
    }

    private void error(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test void normalCardAddsCalendarDaysAndKeepsAnOpenDueDate() {
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T10:00:00+07:00"), 14, "Sinh viên");
        assertThat(dates.borrowDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(dates.originalDueDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(dates.dueDate()).isEqualTo(dates.originalDueDate());
        assertThat(dates.dueAt()).isEqualTo(OffsetDateTime.parse("2026-10-21T17:00:00+07:00"));
        assertThat(dates.loanDays()).isEqualTo(14); assertThat(dates.adjusted()).isFalse();
        assertThat(dates.skippedClosedDates()).isEmpty();
    }

    @Test void differentCardPolicyUsesItsOwnNumberOfDays() {
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T10:00:00+07:00"), 7, "Cán bộ");
        assertThat(dates.dueDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(dates.loanDays()).isEqualTo(7);
    }

    @Test void weeklyClosedSundayMovesToMonday() {
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-04T10:00:00+07:00"), 7, "Sinh viên");
        assertThat(dates.originalDueDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(dates.dueDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(dates.skippedClosedDates()).containsExactly(LocalDate.of(2026, 10, 11));
    }

    @Test void explicitClosureOverridesOpenWeekday() {
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of(holiday("2026-10-21")));
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T10:00:00+07:00"), 14, "Sinh viên");
        assertThat(dates.dueDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(dates.adjusted()).isTrue();
    }

    @Test void consecutiveSpecialClosuresAndWeeklyClosureAreAllSkipped() {
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of(
                holiday("2026-10-21"), holiday("2026-10-22"), holiday("2026-10-23"), holiday("2026-10-24")));
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T10:00:00+07:00"), 14, "Sinh viên");
        assertThat(dates.dueDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(dates.skippedClosedDates()).hasSize(5);
        assertThat(dates.dueAt()).isEqualTo(OffsetDateTime.parse("2026-10-26T17:00:00+07:00"));
    }

    @Test void timezoneAndYearBoundaryUseVietnamDatesAndTheFinalDaysClosingTime() {
        var vietnam = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T18:00:00Z"), 1, "Sinh viên");
        assertThat(vietnam.borrowDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(vietnam.dueDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of(holiday("2027-01-01")));
        var year = service.calculateLoanDates(OffsetDateTime.parse("2026-12-31T23:59:00+07:00"), 1, "Sinh viên");
        assertThat(year.dueAt()).isEqualTo(OffsetDateTime.parse("2027-01-02T12:00:00+07:00"));
    }

    @Test void missingAndInvalidLoanDaysAreRejected() {
        for (int days : new int[]{0, -1, 61}) error("LOAN_POLICY_NOT_CONFIGURED", () ->
                service.calculateLoanDates(OffsetDateTime.now(), days, "Chưa cấu hình"));
    }

    @Test void missingDuplicateAndAllClosedSchedulesDoNotChooseDefaults() {
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(List.of());
        error("WEEKLY_SCHEDULE_INCOMPLETE", () -> service.calculateLoanDates(OffsetDateTime.now(), 14, "Sinh viên"));
        var duplicate = schedule(); duplicate.get(6).setDayOfWeek(1);
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(duplicate);
        error("WEEKLY_SCHEDULE_INCOMPLETE", () -> service.calculateLoanDates(OffsetDateTime.now(), 14, "Sinh viên"));
        var allClosed = schedule(); allClosed.forEach(day -> day.setOpen(false));
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(allClosed);
        error("NEXT_OPEN_DATE_NOT_FOUND", () -> service.calculateLoanDates(OffsetDateTime.now(), 14, "Sinh viên"));
    }

    @Test void invalidOpeningHoursCannotProduceADueTimestamp() {
        var invalid = schedule(); invalid.get(0).setCloseTime(null);
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(invalid);
        error("WEEKLY_SCHEDULE_INVALID", () -> service.calculateLoanDates(OffsetDateTime.now(), 14, "Sinh viên"));
    }

    @Test void closurePeriodsLongerThanOldThousandDayLimitStillFindTheNextOpenDate() {
        LocalDate start = LocalDate.of(2026, 10, 21);
        List<LibraryClosedDate> closures = new ArrayList<>();
        for (int day = 0; day < 1001; day++) closures.add(holiday(start.plusDays(day).toString()));
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(closures);
        var dates = service.calculateLoanDates(OffsetDateTime.parse("2026-10-07T10:00:00+07:00"), 14, "Sinh viên");
        assertThat(dates.dueDate()).isAfter(start.plusDays(1000));
        assertThat(dates.skippedClosedDates()).hasSizeGreaterThanOrEqualTo(1001);
        assertThat(dates.dueDate().getDayOfWeek()).isNotEqualTo(java.time.DayOfWeek.SUNDAY);
    }
}
