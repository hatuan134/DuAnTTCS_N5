package com.duanttcsn5.library.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOverdueContactRequest(
        @NotBlank(message = "Vui lòng nhập ghi chú cho lần liên hệ.")
        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự.")
        String note
) {}
