package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.LibraryClosedDate;
import com.duanttcsn5.library.entity.LibraryWeeklySchedule;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryConfigurationPickupDeadlineTest {
    @Mock WarehouseRepository warehouses;
    @Mock ShelfRepository shelves;
    @Mock LibraryWeeklyScheduleRepository weekly;
    @Mock LibraryClosedDateRepository closed;
    @Mock AuditLogRepository audit;
    LibraryConfigurationService service;

    @BeforeEach void setup() {
        service = new LibraryConfigurationService(warehouses, shelves, weekly, closed, audit);
    }

    private List<LibraryWeeklySchedule> standard() {
        List<LibraryWeeklySchedule> days = new ArrayList<>();
        for (int number = 1; number <= 7; number++) {
            LibraryWeeklySchedule day = new LibraryWeeklySchedule();
            day.setDayOfWeek(number); day.setOpen(number != 7);
            if (day.isOpen()) {
                day.setOpenTime(LocalTime.of(8, 0));
                day.setCloseTime(number == 6 ? LocalTime.NOON : LocalTime.of(17, 0));
            }
            days.add(day);
        }
        return days;
    }

    private void calendar(List<LibraryWeeklySchedule> days, String... holidays) {
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(days);
        List<LibraryClosedDate> dates = new ArrayList<>();
        for (String holiday : holidays) {
            LibraryClosedDate date = new LibraryClosedDate(); date.setClosedDate(LocalDate.parse(holiday)); dates.add(date);
        }
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(dates);
    }

    private void expect(String created, String deadline) {
        assertThat(service.calculateReservationPickupDeadline(OffsetDateTime.parse(created)))
                .isEqualTo(OffsetDateTime.parse(deadline));
    }

    @Test void excludesCreationDateAndUsesClosingTimeOfThirdOpenDay() {
        calendar(standard()); expect("2026-09-30T09:00:00+07:00", "2026-10-03T12:00:00+07:00");
    }
    @Test void skipsWeeklyClosedSunday() {
        calendar(standard()); expect("2026-10-02T16:30:00+07:00", "2026-10-06T17:00:00+07:00");
    }
    @Test void specificClosureOverridesOpenMonday() {
        calendar(standard(), "2026-10-05"); expect("2026-10-02T16:30:00+07:00", "2026-10-07T17:00:00+07:00");
    }
    @Test void consecutiveHolidaysAreSkipped() {
        calendar(standard(), "2026-10-05", "2026-10-06");
        expect("2026-10-02T16:30:00+07:00", "2026-10-08T17:00:00+07:00");
    }
    @Test void holidayOnSundayIsNotCountedTwice() {
        calendar(standard(), "2026-10-04"); expect("2026-10-02T16:30:00+07:00", "2026-10-06T17:00:00+07:00");
    }
    @Test void creationOnClosedDateStillStartsWithNextOpenDate() {
        calendar(standard()); expect("2026-10-04T10:00:00+07:00", "2026-10-07T17:00:00+07:00");
    }
    @Test void creationAfterClosingDoesNotCountThatDate() {
        calendar(standard()); expect("2026-10-02T22:00:00+07:00", "2026-10-06T17:00:00+07:00");
    }
    @Test void convertsInputToVietnamLocalDate() {
        calendar(standard()); expect("2026-10-02T20:00:00Z", "2026-10-07T17:00:00+07:00");
    }
    @Test void handlesYearBoundaryAndHoliday() {
        calendar(standard(), "2027-01-01"); expect("2026-12-30T10:00:00+07:00", "2027-01-04T17:00:00+07:00");
    }
    @Test void honorsCustomWeekdayAndClosingTime() {
        var days = standard();
        for (var day : days) {
            day.setOpen(day.getDayOfWeek() == 7);
            day.setOpenTime(day.isOpen() ? LocalTime.of(9, 0) : null);
            day.setCloseTime(day.isOpen() ? LocalTime.of(14, 30) : null);
        }
        calendar(days); expect("2026-10-03T10:00:00+07:00", "2026-10-18T14:30:00+07:00");
    }
    @Test void rejectsIncompleteCalendar() {
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(standard().subList(0, 6));
        invalid("WEEKLY_SCHEDULE_INCOMPLETE");
    }
    @Test void rejectsDuplicateWeekdays() {
        var days = standard(); days.get(6).setDayOfWeek(1);
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(days);
        invalid("WEEKLY_SCHEDULE_INCOMPLETE");
    }
    @Test void rejectsMissingClosingTime() {
        var days = standard(); days.get(0).setCloseTime(null);
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(days);
        invalid("WEEKLY_SCHEDULE_INVALID");
    }
    @Test void rejectsAllClosedWithoutLoopingForever() {
        var days = standard(); days.forEach(day -> day.setOpen(false));
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(days);
        invalid("PICKUP_DEADLINE_NOT_FOUND");
        verifyNoInteractions(closed);
    }
    private void invalid(String code) {
        assertThatThrownBy(() -> service.calculateReservationPickupDeadline(OffsetDateTime.parse("2026-10-03T10:00:00+07:00")))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
