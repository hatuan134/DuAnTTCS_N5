package com.duanttcsn5.library.dto.libraryconfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PercentageOverduePolicy(Long id, BigDecimal dailyPercent, BigDecimal capPercent,
        OffsetDateTime effectiveAt, OffsetDateTime createdAt, Long createdBy, String creatorName, Long previousPolicyId) {
    public record Request(BigDecimal dailyPercent, BigDecimal capPercent, OffsetDateTime effectiveAt) {}
    public record Page(PercentageOverduePolicy current, java.util.List<PercentageOverduePolicy> history) {}
}
