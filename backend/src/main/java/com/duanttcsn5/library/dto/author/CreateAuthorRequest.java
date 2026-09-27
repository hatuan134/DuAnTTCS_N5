package com.duanttcsn5.library.dto.author;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAuthorRequest(
        @NotBlank(message = "Tên tác giả không được để trống")
        @Size(max = 255, message = "Tên tác giả không được vượt quá 255 ký tự")
        String name,

        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
        String note
) {
}
