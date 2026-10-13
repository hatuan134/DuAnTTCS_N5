package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class IncidentCalculator {
    // Existing payment APIs use JS-safe whole VND. Preserve that monetary boundary.
    public static final long MAX_VND = 9_007_199_254_740_991L;
    private IncidentCalculator() {}
    public static void validateFactor(BigDecimal factor) {
        if (factor == null || factor.signum() <= 0 || factor.stripTrailingZeros().scale() > 2
                || factor.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPENSATION_FACTOR",
                    "Hệ số phải lớn hơn 0, tối đa 2 chữ số thập phân và không vượt 9.999.999.999,99.");
    }
    public static long amount(BigDecimal price, BigDecimal factor) {
        if (price == null || price.signum() <= 0)
            throw new ApiException(HttpStatus.CONFLICT, "MISSING_COVER_PRICE",
                    "Bản sao chưa có giá bìa hợp lệ. Vui lòng cập nhật giá trước khi ghi nhận sự cố.");
        if (factor == null) throw new ApiException(HttpStatus.CONFLICT, "FACTOR_NOT_CONFIGURED",
                "Chưa khai báo hệ số cho loại sự cố này. Vui lòng liên hệ Quản lý thư viện.");
        validateFactor(factor);
        try {
            long amount = price.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact();
            if (amount > MAX_VND) throw new ArithmeticException();
            return amount;
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPENSATION_OVERFLOW", "Số tiền bồi thường vượt giới hạn xử lý.");
        }
    }
}
