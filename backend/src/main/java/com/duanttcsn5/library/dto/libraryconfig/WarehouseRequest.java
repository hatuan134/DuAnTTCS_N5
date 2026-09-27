package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank(message = "Mã kho không được để trống")
        @Size(max = 50, message = "Mã kho không được vượt quá 50 ký tự")
        String code,

        @NotBlank(message = "Tên kho không được để trống")
        @Size(max = 150, message = "Tên kho không được vượt quá 150 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả kho không được vượt quá 500 ký tự")
        String description
) {
}
