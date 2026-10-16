package com.duanttcsn5.library.dto.auth;

public record ValidateResetPasswordTokenResponse(
        boolean valid,
        String email,
        String fullName
) {
}
