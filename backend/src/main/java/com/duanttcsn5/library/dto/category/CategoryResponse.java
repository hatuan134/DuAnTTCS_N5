package com.duanttcsn5.library.dto.category;

import com.duanttcsn5.library.entity.Category;

import java.time.OffsetDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long parentId,
        String parentName,
        int level,
        boolean active,
        long bookCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CategoryResponse fromEntity(Category category, long bookCount) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        String parentName = category.getParent() != null ? category.getParent().getName() : null;
        int level = parentId == null ? 1 : 2;

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription() != null ? category.getDescription() : "",
                parentId,
                parentName,
                level,
                category.isActive(),
                bookCount,
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
