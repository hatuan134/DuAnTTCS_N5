package com.duanttcsn5.library.dto.audit;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        OffsetDateTime timestamp,
        Long actorId,
        String actor,
        String actorRole,
        String action,
        String actionGroup,
        String actionLabel,
        String target,
        String targetType,
        String entityType,
        String entityId,
        String ipAddress,
        String detail
) {
}
