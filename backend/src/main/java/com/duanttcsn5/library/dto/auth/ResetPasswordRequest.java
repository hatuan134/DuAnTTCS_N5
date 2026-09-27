package com.duanttcsn5.library.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Mã xác thực liên kết không được để trống")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Mật khẩu phải chứa ít nhất 1 chữ cái và 1 chữ số"
        )
        String newPassword,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống")
        String confirmPassword
) {
}
