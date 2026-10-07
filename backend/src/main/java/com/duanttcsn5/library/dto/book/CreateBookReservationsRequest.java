package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookReservationsRequest(
        @NotNull(message = "Số lượng đặt giữ không được để trống")
        @Min(value = 1, message = "Số lượng đặt giữ phải từ 1 trở lên")
        @Max(value = 3, message = "Mỗi lần chỉ được đặt giữ tối đa 3 bản")
        Integer quantity
) {
}
