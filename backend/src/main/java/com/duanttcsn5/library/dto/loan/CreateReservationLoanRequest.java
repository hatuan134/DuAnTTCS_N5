package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReservationLoanRequest(
        @NotBlank(message = "Vui lòng nhập mã thẻ của bạn đọc đến nhận sách.")
        @Size(max = 100, message = "Mã thẻ không được vượt quá 100 ký tự.")
        String cardNumber
) {}
