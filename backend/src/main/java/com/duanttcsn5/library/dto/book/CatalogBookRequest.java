package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CatalogBookRequest(
        @NotBlank(message = "Nhan đề không được để trống")
        @Size(max = 255, message = "Nhan đề không được vượt quá 255 ký tự")
        String title,

        @Size(max = 255, message = "Nhan đề phụ không được vượt quá 255 ký tự")
        String subtitle,

        // Giữ authorId/authorName để tương thích API cũ. Frontend S2-01.2 gửi authorIds.
        Long authorId,

        @Size(max = 255, message = "Tên tác giả không được vượt quá 255 ký tự")
        String authorName,

        @NotNull(message = "Vui lòng chọn thể loại")
        Long categoryId,

        @Size(max = 50, message = "Mã ISBN không được vượt quá 50 ký tự")
        String isbn,

        @NotBlank(message = "Vui lòng chọn nhà xuất bản")
        @Size(max = 255, message = "Nhà xuất bản không được vượt quá 255 ký tự")
        String publisher,

        @NotNull(message = "Vui lòng nhập năm xuất bản")
        @Positive(message = "Năm xuất bản phải lớn hơn 0")
        Integer publicationYear,

        @NotNull(message = "Vui lòng nhập số trang")
        @Positive(message = "Số trang phải lớn hơn 0")
        Integer pageCount,

        @Size(max = 1000, message = "Tóm tắt nội dung không được vượt quá 1000 ký tự")
        String description,

        List<Long> authorIds,

        // S2-01.4: chỉ cho phép lưu nhan đề trùng khi thủ thư đã xác nhận rõ ràng.
        Boolean confirmDuplicateTitle
) {
    /**
     * Constructor tương thích các test/client Java cũ của S2-01.1.
     */
    public CatalogBookRequest(
            String title,
            String subtitle,
            Long authorId,
            String authorName,
            Long categoryId,
            String isbn,
            String publisher,
            Integer publicationYear,
            Integer pageCount,
            String description
    ) {
        this(title, subtitle, authorId, authorName, categoryId, isbn, publisher,
                publicationYear, pageCount, description, null, false);
    }

    /**
     * Constructor tương thích S2-01.2/S2-01.3 trước khi có cờ xác nhận nhan đề trùng.
     */
    public CatalogBookRequest(
            String title,
            String subtitle,
            Long authorId,
            String authorName,
            Long categoryId,
            String isbn,
            String publisher,
            Integer publicationYear,
            Integer pageCount,
            String description,
            List<Long> authorIds
    ) {
        this(title, subtitle, authorId, authorName, categoryId, isbn, publisher,
                publicationYear, pageCount, description, authorIds, false);
    }
}
