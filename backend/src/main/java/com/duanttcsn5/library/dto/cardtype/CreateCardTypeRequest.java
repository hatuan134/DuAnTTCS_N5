package com.duanttcsn5.library.dto.cardtype;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCardTypeRequest(
        @NotBlank(message = "Tên loại thẻ không được để trống")
        @Size(max = 100, message = "Tên loại thẻ không được vượt quá 100 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
        String description,

        @Min(value = 1, message = "Thời hạn thẻ tối thiểu 1 tháng")
        @Max(value = 120, message = "Thời hạn thẻ không vượt quá 120 tháng")
        Integer duration,

        @Min(value = 1, message = "Số sách tối đa phải lớn hơn 0")
        @Max(value = 10, message = "Số sách tối đa không được vượt quá 10")
        int maxBooks,

        @Min(value = 1, message = "Số ngày mượn phải lớn hơn 0")
        @Max(value = 60, message = "Số ngày mượn không được vượt quá 60")
        int loanDays,

        @Min(value = 1, message = "Số lần gia hạn tối đa phải lớn hơn 0")
        @Max(value = 10, message = "Số lần gia hạn tối đa không được vượt quá 10")
        int maxRenewals,

        @Min(value = 1, message = "Số ngày mỗi lần gia hạn phải lớn hơn 0")
        @Max(value = 30, message = "Số ngày mỗi lần gia hạn không được vượt quá 30")
        int renewalDays
) {
}
