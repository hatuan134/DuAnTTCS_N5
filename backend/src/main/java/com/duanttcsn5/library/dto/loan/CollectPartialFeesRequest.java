package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CollectPartialFeesRequest(
        @NotNull(message = "Vui lòng cung cấp mã xác nhận giao dịch.") UUID requestKey,
        @NotNull(message = "Vui lòng tra cứu số tiền nợ trước khi thu.")
        @Positive(message = "Tổng nợ xác nhận phải lớn hơn 0 VND.")
        @Digits(integer = 16, fraction = 0, message = "Tổng nợ phải là số nguyên VND.") BigDecimal expectedTotalVnd,
        @NotNull(message = "Vui lòng tra cứu lại danh sách khoản phí.")
        @Pattern(regexp = "[a-f0-9]{64}", message = "Dữ liệu xác nhận khoản phí không hợp lệ.") String debtToken,
        @NotNull(message = "Vui lòng nhập số tiền bạn đọc thực tế nộp.")
        @Positive(message = "Số tiền thu phải lớn hơn 0 VND.")
        @Digits(integer = 16, fraction = 0, message = "Số tiền thu phải là số nguyên VND.") BigDecimal amountVnd
) {
    public CollectPartialFeesRequest(UUID key, long total, String token, long amount) {
        this(key, BigDecimal.valueOf(total), token, BigDecimal.valueOf(amount));
    }
}
