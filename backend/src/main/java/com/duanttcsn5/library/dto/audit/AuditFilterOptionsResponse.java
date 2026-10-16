package com.duanttcsn5.library.dto.audit;

import java.util.List;

public record AuditFilterOptionsResponse(
        List<AuditActorOptionResponse> actors,
        List<AuditActionOptionResponse> actions
) {
}
