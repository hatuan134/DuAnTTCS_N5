package com.duanttcsn5.library.dto.account;

import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        String roleName,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
