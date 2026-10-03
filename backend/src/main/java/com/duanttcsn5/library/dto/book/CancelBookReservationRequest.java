package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookReservationRequest(
        @NotBlank(message = "Vui lòng nhập lý do huỷ đơn.")
        @Size(max = 500, message = "Lý do huỷ không được dài quá 500 ký tự.")
        String reason
) {}
