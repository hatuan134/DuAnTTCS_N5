package com.duanttcsn5.library.dto.libraryconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkClosedDatesRequest(
        @NotEmpty(message = "Danh sách ngày đóng cửa không được để trống")
        @Size(max = 366, message = "Mỗi lần chỉ được khai báo tối đa 366 ngày")
        List<@Valid ClosedDateRequest> dates
) {
}
