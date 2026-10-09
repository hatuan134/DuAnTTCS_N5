package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record OverdueContactResponse(
        Long id, Long loanId, Long staffId, String staffName,
        String note, OffsetDateTime contactedAt
) {}
