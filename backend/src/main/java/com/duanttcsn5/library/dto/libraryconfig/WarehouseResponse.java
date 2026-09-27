package com.duanttcsn5.library.dto.libraryconfig;

import com.duanttcsn5.library.entity.Warehouse;

import java.time.OffsetDateTime;

public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        long shelfCount,
        long copyCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WarehouseResponse fromEntity(Warehouse warehouse, long shelfCount, long copyCount) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getDescription() == null ? "" : warehouse.getDescription(),
                warehouse.isActive(),
                shelfCount,
                copyCount,
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }
}
