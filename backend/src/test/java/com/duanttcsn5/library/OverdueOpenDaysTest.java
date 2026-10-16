package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.LibraryClosedDate;
import com.duanttcsn5.library.entity.LibraryWeeklySchedule;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.LibraryClosedDateRepository;
import com.duanttcsn5.library.repository.LibraryWeeklyScheduleRepository;
import com.duanttcsn5.library.repository.ShelfRepository;
import com.duanttcsn5.library.repository.WarehouseRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OverdueOpenDaysTest {
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private final LibraryWeeklyScheduleRepository weekly = mock(LibraryWeeklyScheduleRepository.class);
    private final LibraryClosedDateRepository closures = mock(LibraryClosedDateRepository.class);
    private LibraryConfigurationService service;

    @BeforeEach
    void setup() {
        service = new LibraryConfigurationService(mock(WarehouseRepository.class),
                mock(ShelfRepository.class), weekly, closures, mock(AuditLogRepository.class));
    }

    private void schedule(Set<Integer> openWeekdays) {
        List<LibraryWeeklySchedule> days = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            LibraryWeeklySchedule day = new LibraryWeeklySchedule();
            day.setDayOfWeek(i);
            day.setOpen(openWeekdays.contains(i));
            if (day.isOpen()) {
                day.setOpenTime(LocalTime.of(8, 0));
                day.setCloseTime(LocalTime.of(17, 0));
            }
            days.add(day);
        }
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(days);
    }

    private void holidays(LocalDate today, LocalDate... dates) {
        List<LibraryClosedDate> values = new ArrayList<>();
        for (LocalDate date : dates) {
            LibraryClosedDate closed = new LibraryClosedDate();
            closed.setClosedDate(date);
            values.add(closed);
        }
        when(closures.findAllByClosedDateBetweenOrderByClosedDateAsc(FRIDAY.plusDays(1), today))
                .thenReturn(values);
    }

    @Test
    void allDatesOpenCountsEveryDayAfterDeadlineIncludingToday() {
        schedule(Set.of(1, 2, 3, 4, 5, 6, 7));
        holidays(MONDAY);
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .containsEntry(FRIDAY, 3L);
    }

    @Test
    void configuredClosedDateIsExcludedEvenIfWeekdayIsNormallyOpen() {
        schedule(Set.of(1, 2, 3, 4, 5, 6, 7));
        holidays(MONDAY, LocalDate.of(2026, 10, 10));
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .containsEntry(FRIDAY, 2L);
    }

    @Test
    void closedWeekendDoesNotCount() {
        schedule(Set.of(1, 2, 3, 4, 5));
        holidays(MONDAY);
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .containsEntry(FRIDAY, 1L);
    }

    @Test
    void configuredHolidayOnMondayDoesNotCount() {
        schedule(Set.of(1, 2, 3, 4, 5));
        holidays(MONDAY, MONDAY);
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .containsEntry(FRIDAY, 0L);
    }

    @Test
    void severalConsecutiveClosuresAreAllExcluded() {
        schedule(Set.of(1, 2, 3, 4, 5, 6, 7));
        holidays(MONDAY, FRIDAY.plusDays(1), FRIDAY.plusDays(2), MONDAY);
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .containsEntry(FRIDAY, 0L);
    }

    @Test
    void dueBeforeVacationStartsCountingAtFirstSubsequentOpenDate() {
        LocalDate tuesday = MONDAY.plusDays(1);
        schedule(Set.of(1, 2, 3, 4, 5));
        holidays(tuesday, MONDAY);
        assertThat(service.calculateOverdueOpenDays(Set.of(FRIDAY), tuesday))
                .containsEntry(FRIDAY, 1L);
    }

    @Test
    void multipleDeadlinesUseOneCalendarQueryAndSubtractTheirOwnDueDates() {
        LocalDate thursday = FRIDAY.minusDays(1);
        LocalDate saturday = FRIDAY.plusDays(1);
        schedule(Set.of(1, 2, 3, 4, 5));
        // Only Friday and Monday are open in [Thursday+1, Monday].
        when(closures.findAllByClosedDateBetweenOrderByClosedDateAsc(thursday.plusDays(1), MONDAY))
                .thenReturn(List.of());
        assertThat(service.calculateOverdueOpenDays(Set.of(thursday, FRIDAY, saturday), MONDAY))
                .isEqualTo(Map.of(thursday, 2L, FRIDAY, 1L, saturday, 1L));
        verify(weekly).findAllByOrderByDayOfWeekAsc();
        verify(closures).findAllByClosedDateBetweenOrderByClosedDateAsc(thursday.plusDays(1), MONDAY);
    }

    @Test
    void emptyDeadlineSetNeedsNoDatabaseAccess() {
        assertThat(service.calculateOverdueOpenDays(Set.of(), MONDAY)).isEmpty();
        verifyNoInteractions(weekly, closures);
    }

    @Test
    void missingWeekdayConfigurationDoesNotGuessWhetherLibraryIsOpen() {
        when(weekly.findAllByOrderByDayOfWeekAsc()).thenReturn(List.of());
        assertThatThrownBy(() -> service.calculateOverdueOpenDays(Set.of(FRIDAY), MONDAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("7 ngày");
        verifyNoInteractions(closures);
    }
}
