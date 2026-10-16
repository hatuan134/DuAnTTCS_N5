package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WeeklyScheduleUpdateRequest(
        @NotEmpty(message = "Lịch tuần không được để trống")
        List<@Valid WeeklyScheduleItemRequest> days
) {
}
