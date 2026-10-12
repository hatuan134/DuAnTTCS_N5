package com.duanttcsn5.library.dto.loan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** The same immutable snapshot is used for preview, persisted return and later review. */
public record ReturnFeeAssessment(
        String status, String message, String policyVersion,
        OffsetDateTime dueAt, OffsetDateTime returnedAt, long overdueDays,
        Long dailyRateVnd, Long rawAmountVnd, Long amountVnd, Long feeId,
        List<LocalDate> excludedDates, List<WeeklyDay> weeklySchedule, List<ClosedDay> closedDates) {
    public ReturnFeeAssessment {
        excludedDates = List.copyOf(excludedDates);
        weeklySchedule = List.copyOf(weeklySchedule);
        closedDates = List.copyOf(closedDates);
    }
    public record WeeklyDay(int dayOfWeek, boolean open, LocalTime openTime, LocalTime closeTime) {}
    public record ClosedDay(LocalDate date, String reason) {}
    public ReturnFeeAssessment withFeeId(Long id) {
        return new ReturnFeeAssessment(status, message, policyVersion, dueAt, returnedAt, overdueDays,
                dailyRateVnd, rawAmountVnd, amountVnd, id, excludedDates, weeklySchedule, closedDates);
    }
}
