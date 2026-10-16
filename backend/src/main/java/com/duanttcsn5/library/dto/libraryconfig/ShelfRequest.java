package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShelfRequest(
        @NotNull(message = "Vui lòng chọn kho")
        Long warehouseId,

        @NotBlank(message = "Mã kệ không được để trống")
        @Size(max = 50, message = "Mã kệ không được vượt quá 50 ký tự")
        String code,

        @NotBlank(message = "Tên kệ không được để trống")
        @Size(max = 150, message = "Tên kệ không được vượt quá 150 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả kệ không được vượt quá 500 ký tự")
        String description
) {
}
