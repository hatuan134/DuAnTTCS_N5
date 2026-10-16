package com.duanttcsn5.library.dto.author;

import com.duanttcsn5.library.entity.Author;

import java.time.OffsetDateTime;

public record AuthorResponse(
        Long id,
        String name,
        String note,
        boolean active,
        long bookCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AuthorResponse fromEntity(Author author, long bookCount) {
        return new AuthorResponse(
                author.getId(),
                author.getName(),
                author.getNote() != null ? author.getNote() : "",
                author.isActive(),
                bookCount,
                author.getCreatedAt(),
                author.getUpdatedAt()
        );
    }
}
