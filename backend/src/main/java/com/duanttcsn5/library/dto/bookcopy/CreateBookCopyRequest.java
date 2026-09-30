package com.duanttcsn5.library.dto.bookcopy;

import com.duanttcsn5.library.entity.PhysicalCondition;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateBookCopyRequest(
        @NotBlank(message = "Vui lòng nhập mã vạch.")
        @Size(max = 100, message = "Mã vạch không được dài quá 100 ký tự.") String barcode,
        @NotNull(message = "Vui lòng chọn kho.")
        @Positive(message = "Kho không hợp lệ.") Long warehouseId,
        @NotNull(message = "Vui lòng chọn kệ.")
        @Positive(message = "Kệ không hợp lệ.") Long shelfId,
        @NotNull(message = "Vui lòng nhập ngày nhập.") LocalDate receivedDate,
        @NotNull(message = "Vui lòng nhập giá bìa.")
        @DecimalMin(value = "0", message = "Giá bìa phải lớn hơn hoặc bằng 0.")
        @Digits(integer = 10, fraction = 2, message = "Giá bìa tối đa 10 chữ số nguyên và 2 chữ số thập phân.") BigDecimal coverPrice,
        @NotNull(message = "Vui lòng chọn tình trạng vật lý.") PhysicalCondition physicalCondition,
        @Null(message = "Đầu sách được xác định từ trang đang mở, không gửi bookId trong biểu mẫu.") Long bookId,
        @Null(message = "Trạng thái được hệ thống tự gán Sẵn sàng.") String status
) {}
