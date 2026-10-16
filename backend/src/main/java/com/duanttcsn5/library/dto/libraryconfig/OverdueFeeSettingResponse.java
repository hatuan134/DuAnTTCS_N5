package com.duanttcsn5.library.dto.libraryconfig;

import java.time.OffsetDateTime;

/** NULL is unconfigured; zero is an explicitly configured free daily rate. */
public record OverdueFeeSettingResponse(Long dailyRateVnd, OffsetDateTime updatedAt, Long updatedBy) {}
