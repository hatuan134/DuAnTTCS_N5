package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectFeeReductionRequest(
        @NotBlank(message = "Vui lòng nhập lý do từ chối.")
        @Size(max = 1000, message = "Lý do từ chối không vượt quá 1000 ký tự.")
        String reason) {}
