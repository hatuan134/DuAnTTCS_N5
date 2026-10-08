package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A deliberate staff check; live GET previews never create a rejection entry. */
public record CheckReaderLoanRequest(
        @NotNull(message = "Vui lòng gửi mã thao tác kiểm tra.") UUID requestId,
        @NotBlank(message = "Vui lòng nhập mã thẻ thư viện.")
        @Size(max = 100, message = "Mã thẻ không được vượt quá 100 ký tự.") String cardNumber
) {}
