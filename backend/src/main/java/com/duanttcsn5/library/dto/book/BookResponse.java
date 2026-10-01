package com.duanttcsn5.library.dto.book;

import com.duanttcsn5.library.entity.Book;

import java.time.OffsetDateTime;

public record BookResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        Long authorId,
        String authorName,
        boolean authorActive,
        Long categoryId,
        String categoryName,
        boolean categoryActive,
        String publisher,
        Integer publicationYear,
        Integer pageCount,
        String description,
        OffsetDateTime createdAt
) {
    public static BookResponse fromEntity(Book book) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getSubtitle(),
                book.getAuthor() != null ? book.getAuthor().getId() : null,
                book.getAuthor() != null ? book.getAuthor().getName() : "Không rõ",
                book.getAuthor() != null && book.getAuthor().isActive(),
                book.getCategory() != null ? book.getCategory().getId() : null,
                book.getCategory() != null ? book.getCategory().getName() : "Không rõ",
                book.getCategory() != null && book.getCategory().isActive(),
                book.getPublisher(),
                book.getPublicationYear(),
                book.getPageCount(),
                book.getDescription(),
                book.getCreatedAt()
        );
    }
}
