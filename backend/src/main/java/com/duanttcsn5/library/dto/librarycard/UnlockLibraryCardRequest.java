package com.duanttcsn5.library.dto.librarycard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Identify the exact lock shown to the manager; reject stale confirmations. */
public record UnlockLibraryCardRequest(
        @NotBlank(message = "Bắt buộc nhập lý do mở khóa thẻ.")
        @Size(max = 1000, message = "Lý do mở khóa không được vượt quá 1000 ký tự.") String reason,
        @NotNull(message = "Thiếu thông tin lần khóa. Vui lòng tải lại chi tiết thẻ.") OffsetDateTime lockStartedAt
) {}
