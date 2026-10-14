package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class IncidentCalculator {
    // Existing payment APIs use JS-safe whole VND. Preserve that monetary boundary.
    public static final long MAX_VND = 9_007_199_254_740_991L;
    public static final long MAX_ADJUSTED_VND = 10_000_000L;
    public record Adjustment(long amount, String reason) {}
    public static Adjustment adjustment(long automatic, String proposed, String reason) {
        if (proposed == null) return new Adjustment(automatic, null);
        String value = proposed.strip();
        if (!value.matches("[0-9]{1,16}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPENSATION_AMOUNT",
                    "Số tiền phải là số nguyên VND không âm, không dùng dấu phân cách.");
        long amount = Long.parseLong(value);
        if (amount == automatic) return new Adjustment(automatic, null);
        if (amount > MAX_ADJUSTED_VND)
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPENSATION_ADJUSTMENT_LIMIT",
                    "Số tiền sau điều chỉnh không được vượt 10.000.000 đồng.");
        String explanation = reason == null ? "" : reason.replace('\u00a0',' ').replace('\u202f',' ').strip();
        if (explanation.isBlank() || explanation.length() > 1900)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ADJUSTMENT_REASON",
                    "Khi đổi tiền, phải nhập lý do từ 1 đến 1.900 ký tự.");
        return new Adjustment(amount, explanation);
    }
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
