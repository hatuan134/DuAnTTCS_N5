package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.WeeklyDay;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.ClosedDay;
import com.duanttcsn5.library.service.ReturnFeeCalculator;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReturnFeeCalculatorTest {
    static OffsetDateTime at(String date) { return OffsetDateTime.parse(date + "T17:00:00+07:00"); }
    static List<WeeklyDay> weekly(boolean weekendOpen) {
        var days = new ArrayList<WeeklyDay>();
        for (int i = 1; i <= 7; i++) days.add(new WeeklyDay(i, weekendOpen || i < 6,
                LocalTime.of(8, 0), LocalTime.of(17, 0)));
        return days;
    }
    static ReturnFeeAssessment calc(String due, String returned, Long rate, List<ClosedDay> closures) {
        return ReturnFeeCalculator.calculate(at(due), at(returned), rate, weekly(true), closures);
    }
    @Test void earlyAndSameDateAreFreeEvenAfterDueHourOrBeforeRateConfigured() {
        assertEquals(0L, calc("2026-10-12", "2026-10-11", null, List.of()).amountVnd());
        var result = ReturnFeeCalculator.calculate(at("2026-10-12").minusHours(7),
                at("2026-10-12"), null, List.of(), List.of());
        assertEquals("READY", result.status()); assertEquals(0L, result.amountVnd());
    }
    @Test void excludesDueDateIncludesActualReturnDateAndCountsEachOpenDayOnce() {
        var one = calc("2026-10-07", "2026-10-08", 5000L, List.of());
        assertEquals(1L, one.overdueDays()); assertEquals(5000L, one.amountVnd());
        var many = calc("2026-10-07", "2026-10-12", 5000L, List.of());
        assertEquals(5L, many.overdueDays()); assertEquals(25000L, many.amountVnd());
    }
    @Test void demoFiveCalendarDaysMinusTwoWeekendClosuresIsThreeDaysAnd15000() {
        var result = ReturnFeeCalculator.calculate(at("2026-10-07"), at("2026-10-12"), 5000L,
                weekly(false), List.of());
        assertEquals(3L, result.overdueDays()); assertEquals(15000L, result.amountVnd());
        assertEquals(List.of(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11")), result.excludedDates());
    }
    @Test void holidayAndSpecialClosureOverrideWeeklyOpeningWithoutCountingOverlapTwice() {
        var closures = List.of(new ClosedDay(LocalDate.parse("2026-10-08"), "Ngày lễ"),
                new ClosedDay(LocalDate.parse("2026-10-09"), "Đóng cửa đặc biệt"),
                new ClosedDay(LocalDate.parse("2026-10-10"), "Ngày lễ trùng thứ Bảy"));
        var result = ReturnFeeCalculator.calculate(at("2026-10-07"), at("2026-10-12"), 5000L,
                weekly(false), closures);
        assertEquals(1L, result.overdueDays()); assertEquals(5000L, result.amountVnd());
        assertEquals(4, result.excludedDates().size()); assertEquals(closures, result.closedDates());
    }
    @Test void actualClosedReturnDateIsAcceptedAndNotChargedOrMoved() {
        var result = ReturnFeeCalculator.calculate(at("2026-10-09"), at("2026-10-11"), 5000L,
                weekly(false), List.of());
        assertEquals("READY", result.status()); assertEquals(at("2026-10-11"), result.returnedAt());
        assertEquals(0L, result.overdueDays()); assertEquals(0L, result.amountVnd());
    }
    @Test void noOpenDaysIsZeroEvenWithoutRateAndAllClosedWeeklyScheduleIsValidForFees() {
        var closed = weekly(false).stream().map(d -> new WeeklyDay(d.dayOfWeek(), false, null, null)).toList();
        var result = ReturnFeeCalculator.calculate(at("2026-10-07"), at("2026-10-12"), null, closed, List.of());
        assertEquals("READY", result.status()); assertEquals(0L, result.amountVnd());
    }
    @Test void halfUpRoundsTotalOnceIncludingExact500AndZero() {
        long[][] cases = {{0,0},{1,0},{499,0},{500,1000},{501,1000},{999,1000},
                {1000,1000},{1499,1000},{1500,2000},{1501,2000},{2499,2000},{2500,3000}};
        for (long[] c : cases) {
            var result = calc("2026-10-07", "2026-10-08", c[0], List.of());
            assertEquals(c[0], result.rawAmountVnd()); assertEquals(c[1], result.amountVnd());
        }
        // 2 x 499 = 998 -> 1,000; do not round each day to zero.
        assertEquals(1000L, calc("2026-10-07", "2026-10-09", 499L, List.of()).amountVnd());
    }
    @Test void missingRateIsUnpricedInsteadOfInventingZeroForOpenOverdueDays() {
        var result = calc("2026-10-07", "2026-10-08", null, List.of());
        assertEquals("OVERDUE_FEE_NOT_CONFIGURED", result.status());
        assertNull(result.amountVnd()); assertEquals(1L, result.overdueDays());
    }
    @Test void invalidPriceOrIncompleteCalendarIsAnExplicitError() {
        assertEquals("OVERDUE_FEE_INVALID", calc("2026-10-07", "2026-10-08", -1L, List.of()).status());
        assertEquals("OVERDUE_FEE_INVALID", calc("2026-10-07", "2026-10-08", 1000000001L, List.of()).status());
        var result = ReturnFeeCalculator.calculate(at("2026-10-07"), at("2026-10-08"), 5000L,
                weekly(true).subList(0, 6), List.of());
        assertEquals("WEEKLY_SCHEDULE_INVALID", result.status()); assertNull(result.amountVnd());
    }
    @Test void vietnamLocalDatesIgnoreUtcDateBoundaryAndOriginalOffset() {
        var result = ReturnFeeCalculator.calculate(OffsetDateTime.parse("2026-10-08T18:00:00Z"),
                OffsetDateTime.parse("2026-10-09T16:59:00Z"), 5000L, weekly(true), List.of());
        assertEquals(0L, result.overdueDays());
        assertEquals(1L, ReturnFeeCalculator.calculate(OffsetDateTime.parse("2026-10-08T16:59:00Z"),
                OffsetDateTime.parse("2026-10-08T17:01:00Z"), 5000L, weekly(true), List.of()).overdueDays());
    }
    @Test void snapshotCannotBeChangedByMutatingInputListsAndKeepsOriginalRate() {
        var schedule = new ArrayList<>(weekly(true));
        var closures = new ArrayList<ClosedDay>();
        var result = ReturnFeeCalculator.calculate(at("2026-10-07"), at("2026-10-08"), 5000L, schedule, closures);
        schedule.clear(); closures.add(new ClosedDay(LocalDate.parse("2026-10-08"), "Thay đổi sau"));
        assertEquals(7, result.weeklySchedule().size()); assertEquals(5000L, result.amountVnd());
        assertEquals(5000L, result.dailyRateVnd()); assertTrue(result.closedDates().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.weeklySchedule().clear());
    }
    @Test void leapDayAndYearBoundaryAreCountedAsNormalOpenDays() {
        assertEquals(2L, calc("2024-02-28", "2024-03-01", 5000L, List.of()).overdueDays());
        assertEquals(1L, calc("2026-12-31", "2027-01-01", 5000L, List.of()).overdueDays());
    }
}
