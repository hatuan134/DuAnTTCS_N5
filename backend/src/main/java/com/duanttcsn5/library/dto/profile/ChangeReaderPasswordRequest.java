package com.duanttcsn5.library.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeReaderPasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống.")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống.")
        @Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự.")
        String newPassword,

        @NotBlank(message = "Vui lòng nhập lại mật khẩu mới.")
        String confirmPassword
) {
}
