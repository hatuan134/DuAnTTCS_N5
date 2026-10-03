package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record ReservationCancellationAuditResponse(
        Long actorId, String actorName, OffsetDateTime cancelledAt, String reason
) {}
