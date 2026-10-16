package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Bind confirmation to the item actually previewed, never to a later loan of the same copy. */
public record ConfirmReturnRequest(
        @NotBlank(message = "Vui lòng nhập mã vạch bản sao.")
        @Size(max = 100, message = "Mã vạch không được vượt quá 100 ký tự.") String barcode,
        @NotNull(message = "Vui lòng tra cứu phiếu mượn trước khi xác nhận.")
        @Positive(message = "Mã chi tiết phiếu mượn không hợp lệ.") Long itemId) {
}
