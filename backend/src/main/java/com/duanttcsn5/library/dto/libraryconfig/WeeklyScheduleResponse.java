package com.duanttcsn5.library.dto.libraryconfig;

import com.duanttcsn5.library.entity.LibraryWeeklySchedule;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record WeeklyScheduleResponse(
        Long id,
        int dayOfWeek,
        String dayLabel,
        boolean open,
        LocalTime openTime,
        LocalTime closeTime,
        OffsetDateTime updatedAt,
        Long updatedBy
) {
    public static WeeklyScheduleResponse fromEntity(LibraryWeeklySchedule schedule) {
        return new WeeklyScheduleResponse(
                schedule.getId(),
                schedule.getDayOfWeek(),
                labelOf(schedule.getDayOfWeek()),
                schedule.isOpen(),
                schedule.getOpenTime(),
                schedule.getCloseTime(),
                schedule.getUpdatedAt(),
                schedule.getUpdatedBy()
        );
    }

    private static String labelOf(int day) {
        return switch (day) {
            case 1 -> "Thứ Hai";
            case 2 -> "Thứ Ba";
            case 3 -> "Thứ Tư";
            case 4 -> "Thứ Năm";
            case 5 -> "Thứ Sáu";
            case 6 -> "Thứ Bảy";
            case 7 -> "Chủ Nhật";
            default -> "Không xác định";
        };
    }
}
