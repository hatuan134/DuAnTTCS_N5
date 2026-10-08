package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReservationLoanRequest(
        @NotBlank(message = "Vui lòng nhập mã thẻ của bạn đọc đến nhận sách.")
        @Size(max = 100, message = "Mã thẻ không được vượt quá 100 ký tự.")
        String cardNumber,
        LocalDate expectedBorrowDate,
        OffsetDateTime expectedDueAt,
        @Min(value = 1, message = "Số ngày mượn phải từ 1 đến 60.")
        @Max(value = 60, message = "Số ngày mượn phải từ 1 đến 60.")
        Integer expectedLoanDays,
        UUID requestId
) {
    public CreateReservationLoanRequest(String cardNumber) {
        this(cardNumber, null, null, null, null);
    }

    public CreateReservationLoanRequest(String cardNumber, LocalDate expectedBorrowDate,
            OffsetDateTime expectedDueAt, Integer expectedLoanDays) {
        this(cardNumber, expectedBorrowDate, expectedDueAt, expectedLoanDays, null);
    }
}
