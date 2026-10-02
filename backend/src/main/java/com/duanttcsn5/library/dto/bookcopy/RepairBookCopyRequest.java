package com.duanttcsn5.library.dto.bookcopy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RepairBookCopyRequest(
        @NotBlank(message = "Vui lòng nhập lý do sửa chữa.")
        @Size(max = 2000, message = "Lý do không được dài quá 2000 ký tự.") String reason) {}
