package com.duanttcsn5.library.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Mã làm mới phiên đăng nhập không được để trống")
        String refreshToken
) {
}
