package com.duanttcsn5.library.dto.reader;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ReaderRegistrationRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Mã sinh viên hoặc mã cán bộ không được để trống")
        @Size(max = 100, message = "Mã định danh tối đa 100 ký tự")
        String memberCode,

        @NotNull(message = "Ngày sinh không được để trống")
        LocalDate dateOfBirth,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự")
        String password
) {
}
