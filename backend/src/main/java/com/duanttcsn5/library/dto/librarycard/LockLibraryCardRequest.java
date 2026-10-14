package com.duanttcsn5.library.dto.librarycard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** null endsAt means an indefinite lock. Non-null values must include an offset. */
public record LockLibraryCardRequest(
        @NotBlank(message = "Bắt buộc nhập lý do khóa thẻ.")
        @Size(max = 1000, message = "Lý do khóa thẻ không được vượt quá 1000 ký tự.") String reason,
        OffsetDateTime endsAt
) {}
