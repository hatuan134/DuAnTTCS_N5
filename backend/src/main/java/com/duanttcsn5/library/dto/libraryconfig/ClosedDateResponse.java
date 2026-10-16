package com.duanttcsn5.library.dto.libraryconfig;

import com.duanttcsn5.library.entity.LibraryClosedDate;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ClosedDateResponse(
        Long id,
        LocalDate closedDate,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long createdBy
) {
    public static ClosedDateResponse fromEntity(LibraryClosedDate value) {
        return new ClosedDateResponse(
                value.getId(),
                value.getClosedDate(),
                value.getReason() == null ? "" : value.getReason(),
                value.getCreatedAt(),
                value.getUpdatedAt(),
                value.getCreatedBy()
        );
    }
}
