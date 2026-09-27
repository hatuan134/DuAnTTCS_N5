package com.duanttcsn5.library.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email,

        @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
        String phone,

        @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
        String address,

        String currentPassword
) {
}
