package com.duanttcsn5.library.dto.librarycard;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ApproveLibraryCardRequest(
        @NotNull(message = "Vui lòng chọn loại thẻ")
        Long cardTypeId,

        @NotNull(message = "Vui lòng chọn ngày hết hạn thẻ")
        @FutureOrPresent(message = "Ngày hết hạn thẻ không được ở trong quá khứ")
        LocalDate expiresAt
) {
}
