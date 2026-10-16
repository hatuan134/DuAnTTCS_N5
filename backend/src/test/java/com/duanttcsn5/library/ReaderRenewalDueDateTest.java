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

/** S3-05.5: exercise the real weekly/special closure calculation, not the test calendar stub. */
class ReaderRenewalDueDateTest {
    private LibraryWeeklyScheduleRepository weekly;
    private LibraryClosedDateRepository closed;
    private LibraryConfigurationService calendar;

    @BeforeEach void setup() {
        weekly = mock(LibraryWeeklyScheduleRepository.class);
        closed = mock(LibraryClosedDateRepository.class);
        calendar = new LibraryConfigurationService(mock(WarehouseRepository.class), mock(ShelfRepository.class),
                weekly, closed, mock(AuditLogRepository.class));
        List<LibraryWeeklySchedule> rows = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            var row = new LibraryWeeklySchedule();
            row.setDayOfWeek(day); row.setOpen(day != 7);
            if (row.isOpen()) {
                row.setOpenTime(LocalTime.of(8, 0));
                row.setCloseTime(day == 6 ? LocalTime.NOON : LocalTime.of(17, 0));
            }
            rows.add(row);
        }
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(rows);
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of());
    }

    private LibraryClosedDate closure(String value) {
        var item = new LibraryClosedDate();
        item.setClosedDate(LocalDate.parse(value)); item.setReason("Đóng cửa kiểm thử");
        return item;
    }

    @Test void normalWeekdayAddsCardPolicyCalendarDays() {
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-07T15:00:00+07:00"), 7))
                .isEqualTo(OffsetDateTime.parse("2026-10-14T17:00:00+07:00"));
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-07T00:00:00Z"), 1))
                .isEqualTo(OffsetDateTime.parse("2026-10-08T17:00:00+07:00"));
    }

    @Test void closedSundayIsMovedToMonday() {
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-04T10:00:00+07:00"), 7))
                .isEqualTo(OffsetDateTime.parse("2026-10-12T17:00:00+07:00"));
    }

    @Test void explicitlyClosedWeekdayIsSkipped() {
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of(closure("2026-10-21")));
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-07T17:00:00+07:00"), 14))
                .isEqualTo(OffsetDateTime.parse("2026-10-22T17:00:00+07:00"));
    }

    @Test void consecutiveClosuresIncludingSundayFindNextOpenDay() {
        when(closed.findAllByOrderByClosedDateAsc()).thenReturn(List.of(closure("2026-10-21"),
                closure("2026-10-22"), closure("2026-10-23"), closure("2026-10-24")));
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-07T17:00:00+07:00"), 14))
                .isEqualTo(OffsetDateTime.parse("2026-10-26T17:00:00+07:00"));
    }

    @Test void anotherTimezoneAndSaturdayUseVietnamClosingHour() {
        assertThat(calendar.calculateRenewalDueAt(OffsetDateTime.parse("2026-10-09T17:00:00Z"), 7))
                .isEqualTo(OffsetDateTime.parse("2026-10-17T12:00:00+07:00"));
    }

    @Test void invalidDaysOrIncompleteScheduleFailWithoutAssumingOpenDays() {
        assertThatThrownBy(() -> calendar.calculateRenewalDueAt(OffsetDateTime.now(), 0))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("RENEWAL_DAYS_NOT_CONFIGURED"));
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(List.of());
        assertThatThrownBy(() -> calendar.calculateRenewalDueAt(OffsetDateTime.now(), 7))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("WEEKLY_SCHEDULE_INCOMPLETE"));
    }
}
