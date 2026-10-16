package com.duanttcsn5.library.dto.cardtype;

import java.time.OffsetDateTime;

public record PolicyHistoryResponse(
        Long id,
        String cardTypeName,
        String action,
        String changedBy,
        OffsetDateTime changedAt,
        String before,
        String after
) {
}
