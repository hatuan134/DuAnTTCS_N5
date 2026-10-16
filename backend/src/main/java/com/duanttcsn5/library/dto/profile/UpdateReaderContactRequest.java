package com.duanttcsn5.library.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateReaderContactRequest(
        @NotBlank(message = "Số điện thoại không được để trống.")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.")
        String phone,

        @NotBlank(message = "Địa chỉ không được để trống.")
        @Size(min = 5, max = 500, message = "Địa chỉ phải có từ 5 đến 500 ký tự.")
        String address,

        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
        String email,

        String currentPassword
) {
}
