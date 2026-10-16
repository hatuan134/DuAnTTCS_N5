package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WeeklyScheduleItemRequest(
        @Min(value = 1, message = "Thứ trong tuần phải từ 1 đến 7")
        @Max(value = 7, message = "Thứ trong tuần phải từ 1 đến 7")
        int dayOfWeek,

        boolean open,

        LocalTime openTime,

        LocalTime closeTime
) {
}
