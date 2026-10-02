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
        LocalDate receivedDate,
        Boolean confirmed,
        Long expectedStartNumber,
        java.util.List<String> expectedSkippedBarcodes
) {
    public BulkCreateBookCopiesRequest(BigDecimal quantity, Long warehouseId, Long shelfId,
            LocalDate receivedDate, Boolean confirmed, Long expectedStartNumber) {
        this(quantity, warehouseId, shelfId, receivedDate, confirmed, expectedStartNumber, java.util.List.of());
    }
    public BulkCreateBookCopiesRequest(BigDecimal quantity, Long warehouseId, Long shelfId, LocalDate receivedDate) {
        this(quantity, warehouseId, shelfId, receivedDate, false, null);
    }
    @AssertTrue(message = "Số lượng bản sao phải là số nguyên từ 1 đến 50.")
    public boolean isQuantityInteger() {
        return quantity == null || quantity.stripTrailingZeros().scale() <= 0;
    }
}
