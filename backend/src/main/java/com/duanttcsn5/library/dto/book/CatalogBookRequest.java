package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CatalogBookRequest(
        @NotBlank(message = "Tên đầu sách không được để trống")
        @Size(max = 255, message = "Tên đầu sách không được vượt quá 255 ký tự")
        String title,

        Long authorId,

        @Size(max = 255, message = "Tên tác giả không được vượt quá 255 ký tự")
        String authorName,

        @NotNull(message = "Vui lòng chọn thể loại")
        Long categoryId,

        @Size(max = 50, message = "Mã ISBN không được vượt quá 50 ký tự")
        String isbn,

        @Size(max = 255, message = "Nhà xuất bản không được vượt quá 255 ký tự")
        String publisher,

        Integer publicationYear,

        @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
        String description
) {
}
