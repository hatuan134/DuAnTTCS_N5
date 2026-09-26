package com.duanttcsn5.library.dto.account;

public record ValidateInitialPasswordTokenResponse(
        boolean valid,
        String email,
        String fullName
) {
}
