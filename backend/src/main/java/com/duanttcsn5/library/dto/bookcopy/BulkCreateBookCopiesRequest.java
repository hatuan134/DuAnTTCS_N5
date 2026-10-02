package com.duanttcsn5.library.dto.bookcopy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BulkCreateBookCopiesRequest(
        @NotNull(message = "Vui lòng nhập số lượng bản sao.")
        @DecimalMin(value = "1", message = "Số lượng bản sao phải từ 1 đến 50.")
        @DecimalMax(value = "50", message = "Số lượng bản sao phải từ 1 đến 50.")
        @Digits(integer = 2, fraction = 0, message = "Số lượng bản sao phải là số nguyên từ 1 đến 50.")
        BigDecimal quantity,

        @NotNull(message = "Vui lòng chọn kho.")
        @Positive(message = "Kho không hợp lệ.")
        Long warehouseId,

        @NotNull(message = "Vui lòng chọn kệ.")
        @Positive(message = "Kệ không hợp lệ.")
        Long shelfId,

        @NotNull(message = "Vui lòng nhập ngày nhập.")
        LocalDate receivedDate
) {
    @AssertTrue(message = "Số lượng bản sao phải là số nguyên từ 1 đến 50.")
    public boolean isQuantityInteger() {
        return quantity == null || quantity.stripTrailingZeros().scale() <= 0;
    }
}
