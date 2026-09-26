package com.duanttcsn5.library.dto.common;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String message,
        String code,
        OffsetDateTime timestamp
) {
}
