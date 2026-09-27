package com.duanttcsn5.library.dto.libraryconfig;

import java.time.LocalDate;
import java.util.List;

public record DueDateAdjustmentResponse(
        LocalDate originalDate,
        LocalDate adjustedDate,
        boolean adjusted,
        List<LocalDate> skippedClosedDates
) {
}
