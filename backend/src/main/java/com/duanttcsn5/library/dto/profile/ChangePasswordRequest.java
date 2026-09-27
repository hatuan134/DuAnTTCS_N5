package com.duanttcsn5.library.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mật khẩu mới phải có cả chữ và số"
        )
        String newPassword,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống")
        String confirmPassword
) {
}
