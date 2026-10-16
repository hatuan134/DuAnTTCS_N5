package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.WeeklyDay;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.ClosedDay;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Exact integer VND arithmetic. No database writes or current-time reads. */
public final class ReturnFeeCalculator {
    public static final String POLICY = "S4-01.1-OPEN-DAYS-HALF-UP-V1";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private ReturnFeeCalculator() {}

    public static ReturnFeeAssessment calculate(OffsetDateTime dueAt, OffsetDateTime returnedAt,
            Long rate, List<WeeklyDay> weekly, List<ClosedDay> closures) {
        if (dueAt == null) return result("MISSING_DUE_DATE", "Phiếu chưa có hạn trả; chưa thể tính phí.",
                dueAt, returnedAt, 0, rate, null, null, List.of(), weekly, closures);
        LocalDate due = dueAt.atZoneSameInstant(ZONE).toLocalDate();
        LocalDate returned = returnedAt.atZoneSameInstant(ZONE).toLocalDate();
        if (!returned.isAfter(due)) return result("READY", "Không phát sinh phí trễ hạn.",
                dueAt, returnedAt, 0, rate, 0L, 0L, List.of(), weekly, closures);
        var byDay = new HashMap<Integer, WeeklyDay>();
        for (var day : weekly) {
            if (day.dayOfWeek() < 1 || day.dayOfWeek() > 7 || byDay.put(day.dayOfWeek(), day) != null
                    || (day.open() && (day.openTime() == null || day.closeTime() == null
                        || !day.closeTime().isAfter(day.openTime())))) {
                return invalidCalendar(dueAt, returnedAt, rate, weekly, closures);
            }
        }
        if (byDay.size() != 7) return invalidCalendar(dueAt, returnedAt, rate, weekly, closures);
        // All seven days closed is a valid zero-open-day interval for fee calculation.
        // Loan creation still uses its existing rule requiring a next open date.
        var closed = new HashSet<LocalDate>();
        for (var date : closures) closed.add(date.date());
        var excluded = new ArrayList<LocalDate>();
        long days = 0;
        for (LocalDate date = due.plusDays(1); !date.isAfter(returned); date = date.plusDays(1)) {
            if (byDay.get(date.getDayOfWeek().getValue()).open() && !closed.contains(date)) days++;
            else excluded.add(date);
        }
        if (days == 0) return result("READY", "Không có ngày mở cửa trễ hạn; phí bằng 0 VND.",
                dueAt, returnedAt, 0, rate, 0L, 0L, excluded, weekly, closures);
        if (rate == null) return result("OVERDUE_FEE_NOT_CONFIGURED",
                "Chưa khai báo mức phí trễ hạn. Vui lòng liên hệ Quản lý thư viện trước khi nhận trả.",
                dueAt, returnedAt, days, null, null, null, excluded, weekly, closures);
        if (rate < 0 || rate > 1_000_000_000L) return result("OVERDUE_FEE_INVALID",
                "Mức phí trễ hạn không hợp lệ. Vui lòng kiểm tra cấu hình thư viện.",
                dueAt, returnedAt, days, rate, null, null, excluded, weekly, closures);
        try {
            long raw = Math.multiplyExact(days, rate);
            long rounded = Math.multiplyExact(Math.addExact(raw, 500L) / 1000L, 1000L);
            return result("READY", rounded == 0 ? "Không phát sinh khoản phải thu."
                    : "Phí trễ hạn được làm tròn đến 1.000 VND.",
                    dueAt, returnedAt, days, rate, raw, rounded, excluded, weekly, closures);
        } catch (ArithmeticException error) {
            return result("OVERDUE_FEE_AMOUNT_OVERFLOW", "Số tiền vượt giới hạn xử lý. Vui lòng kiểm tra phiếu mượn.",
                    dueAt, returnedAt, days, rate, null, null, excluded, weekly, closures);
        }
    }
    private static ReturnFeeAssessment invalidCalendar(OffsetDateTime due, OffsetDateTime returned,
            Long rate, List<WeeklyDay> weekly, List<ClosedDay> closures) {
        return result("WEEKLY_SCHEDULE_INVALID", "Lịch thư viện chưa đủ 7 ngày hoặc giờ mở cửa không hợp lệ; chưa thể tính phí.",
                due, returned, 0, rate, null, null, List.of(), weekly, closures);
    }
    private static ReturnFeeAssessment result(String status, String message, OffsetDateTime due,
            OffsetDateTime returned, long days, Long rate, Long raw, Long amount,
            List<LocalDate> excluded, List<WeeklyDay> weekly, List<ClosedDay> closures) {
        return new ReturnFeeAssessment(status, message, POLICY, due, returned, days, rate, raw, amount,
                null, excluded, weekly, closures);
    }
}
