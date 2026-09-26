package com.duanttcsn5.library.dto.auth;

public record AuthUserResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
