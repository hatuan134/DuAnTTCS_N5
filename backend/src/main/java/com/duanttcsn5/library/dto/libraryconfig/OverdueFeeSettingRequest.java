package com.duanttcsn5.library.dto.libraryconfig;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record OverdueFeeSettingRequest(
        @NotNull(message = "Vui lòng nhập mức phí trễ hạn mỗi ngày.")
        @DecimalMin(value = "0", message = "Mức phí không được âm.")
        @DecimalMax(value = "1000000000", message = "Mức phí không được vượt quá 1.000.000.000 VND/ngày.")
        @Digits(integer = 10, fraction = 0, message = "Mức phí phải là số nguyên VND, không có phần thập phân.")
        BigDecimal dailyRateVnd) {}
