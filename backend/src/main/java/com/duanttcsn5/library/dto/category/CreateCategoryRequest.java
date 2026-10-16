package com.duanttcsn5.library.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Tên thể loại không được để trống")
        @Size(max = 255, message = "Tên thể loại không được vượt quá 255 ký tự")
        String name,

        @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
        String description,

        Long parentId
) {
}
