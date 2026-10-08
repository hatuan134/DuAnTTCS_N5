package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** One request ID belongs to one immutable confirmation payload. */
public record CreateDirectLoanRequest(
        @NotNull(message = "Vui lòng gửi mã xác nhận của lượt mượn.") UUID requestId,
        @NotBlank(message = "Vui lòng nhập mã thẻ thư viện.")
        @Size(max = 100, message = "Mã thẻ không được vượt quá 100 ký tự.") String cardNumber,
        @NotNull(message = "Vui lòng gửi danh sách sách cần mượn.")
        @Size(min = 1, max = 10, message = "Lượt mượn phải có từ 1 đến 10 sách.")
        List<@NotBlank(message = "Mã vạch trong danh sách không được để trống.")
             @Size(max = 100, message = "Mã vạch không được vượt quá 100 ký tự.") String> barcodes,
        boolean overrideRequested,
        @Size(max = 500, message = "Lý do bỏ qua không được vượt quá 500 ký tự.") String overrideReason
) {
    public CreateDirectLoanRequest(UUID requestId, String cardNumber, List<String> barcodes) {
        this(requestId, cardNumber, barcodes, false, null);
    }
}
