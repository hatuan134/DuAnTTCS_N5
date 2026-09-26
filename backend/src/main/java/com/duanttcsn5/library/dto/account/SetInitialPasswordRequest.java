package com.duanttcsn5.library.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetInitialPasswordRequest(
        @NotBlank(message = "Token không được để trống")
        String token,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải chứa ít nhất 1 chữ cái và 1 chữ số")
        String password
) {
}
