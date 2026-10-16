package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClosedDateRequest(
        @NotNull(message = "Ngày đóng cửa không được để trống")
        LocalDate closedDate,

        @NotBlank(message = "Lý do đóng cửa không được để trống")
        @Size(max = 500, message = "Lý do đóng cửa không được vượt quá 500 ký tự")
        String reason
) {
}
