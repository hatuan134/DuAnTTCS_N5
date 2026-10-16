package com.duanttcsn5.library.dto.bookcopy;

import com.duanttcsn5.library.entity.PhysicalCondition;
import jakarta.validation.constraints.*;

/** Complete editable fields for both PUT and PATCH. Identity fields are forbidden. */
public record UpdateBookCopyRequest(
        @NotNull(message = "Vui lòng chọn kho.")
        @Positive(message = "Kho không hợp lệ.") Long warehouseId,
        @NotNull(message = "Vui lòng chọn kệ.")
        @Positive(message = "Kệ không hợp lệ.") Long shelfId,
        @NotNull(message = "Vui lòng chọn tình trạng vật lý.") PhysicalCondition physicalCondition,
        @Size(max = 2000, message = "Ghi chú không được dài quá 2000 ký tự.") String notes,
        @Null(message = "Không được thay đổi mã vạch của bản sao.") String barcode,
        @Null(message = "Không được chuyển bản sao sang đầu sách khác.") Long bookId,
        @Null(message = "Chức năng này không cho phép thay đổi trạng thái bản sao.") String status
) {}
