package com.duanttcsn5.library.dto.audit;

public record AuditActorOptionResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
