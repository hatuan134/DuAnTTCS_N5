package com.duanttcsn5.library.dto.librarycard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReaderApplicationRequest(
        @NotBlank(message = "Lý do từ chối là bắt buộc")
        @Size(max = 1000, message = "Lý do từ chối tối đa 1000 ký tự")
        String reason
) {
}
