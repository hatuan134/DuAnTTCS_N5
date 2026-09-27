package com.duanttcsn5.library.dto.libraryconfig;

import com.duanttcsn5.library.entity.Shelf;

import java.time.OffsetDateTime;

public record ShelfResponse(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String code,
        String name,
        String description,
        boolean active,
        long copyCount,
        boolean inUse,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ShelfResponse fromEntity(Shelf shelf, long copyCount) {
        return new ShelfResponse(
                shelf.getId(),
                shelf.getWarehouse().getId(),
                shelf.getWarehouse().getCode(),
                shelf.getWarehouse().getName(),
                shelf.getCode(),
                shelf.getName() == null ? "" : shelf.getName(),
                shelf.getDescription() == null ? "" : shelf.getDescription(),
                shelf.isActive(),
                copyCount,
                copyCount > 0,
                shelf.getCreatedAt(),
                shelf.getUpdatedAt()
        );
    }
}
