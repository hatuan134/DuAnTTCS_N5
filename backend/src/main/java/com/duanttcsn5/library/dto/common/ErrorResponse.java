package com.duanttcsn5.library.dto.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(
        String message,
        String code,
        OffsetDateTime timestamp,
        Map<String, Object> details
) {
}
