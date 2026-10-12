package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateFeeReductionRequest(
        @NotNull(message = "Vui lòng nhập số tiền đề nghị miễn giảm.")
        @DecimalMin(value = "0", inclusive = false, message = "Số tiền đề nghị phải lớn hơn 0 VND.")
        @Digits(integer = 19, fraction = 0, message = "Số tiền đề nghị phải là số nguyên VND.")
        BigDecimal amountVnd,
        @NotBlank(message = "Vui lòng nhập lý do đề nghị miễn giảm.")
        @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự.")
        String reason) {}
