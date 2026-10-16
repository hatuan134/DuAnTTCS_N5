package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePublisherRequest(
        @NotBlank(message = "Tên nhà xuất bản không được để trống.")
        @Size(max = 255, message = "Tên nhà xuất bản không được vượt quá 255 ký tự.")
        String name) { }
