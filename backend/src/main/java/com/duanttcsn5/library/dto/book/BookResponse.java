package com.duanttcsn5.library.dto.book;

import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BookResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        Long authorId,
        String authorName,
        boolean authorActive,
        List<BookAuthorResponse> authors,
        Long categoryId,
        String categoryName,
        boolean categoryActive,
        String publisher,
        Integer publicationYear,
        Integer pageCount,
        String description,
        OffsetDateTime createdAt,
        long copyCount,
        boolean hasCopies
) {
    /**
     * Constructor tương thích với các call-site/test cũ trước S2-01.5.
     * Khi không truyền số bản sao, mặc định đầu sách chưa có bản sao.
     */
    public BookResponse(
            Long id,
            String isbn,
            String title,
            String subtitle,
            Long authorId,
            String authorName,
            boolean authorActive,
            List<BookAuthorResponse> authors,
            Long categoryId,
            String categoryName,
            boolean categoryActive,
            String publisher,
            Integer publicationYear,
            Integer pageCount,
            String description,
            OffsetDateTime createdAt
    ) {
        this(
                id,
                isbn,
                title,
                subtitle,
                authorId,
                authorName,
                authorActive,
                authors,
                categoryId,
                categoryName,
                categoryActive,
                publisher,
                publicationYear,
                pageCount,
                description,
                createdAt,
                0L,
                false
        );
    }

    public static BookResponse fromEntity(Book book) {
        return fromEntity(book, 0L);
    }

    public static BookResponse fromEntity(Book book, long copyCount) {
        List<BookAuthorResponse> authorResponses = toAuthorResponses(book);
        BookAuthorResponse primary = resolvePrimaryAuthor(book, authorResponses);
        long normalizedCopyCount = Math.max(0L, copyCount);

        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getSubtitle(),
                primary != null ? primary.id() : null,
                primary != null ? primary.name() : "Không rõ",
                primary != null && primary.active(),
                authorResponses,
                book.getCategory() != null ? book.getCategory().getId() : null,
                book.getCategory() != null ? book.getCategory().getName() : "Không rõ",
                book.getCategory() != null && book.getCategory().isActive(),
                book.getPublisher(),
                book.getPublicationYear(),
                book.getPageCount(),
                book.getDescription(),
                book.getCreatedAt(),
                normalizedCopyCount,
                normalizedCopyCount > 0
        );
    }

    private static List<BookAuthorResponse> toAuthorResponses(Book book) {
        Map<Long, BookAuthorResponse> unique = new LinkedHashMap<>();

        if (book.getAuthors() != null) {
            for (Author author : book.getAuthors()) {
                if (author != null && author.getId() != null) {
                    unique.put(author.getId(), BookAuthorResponse.fromEntity(author));
                }
            }
        }

        Author legacyPrimary = book.getAuthor();
        if (legacyPrimary != null && legacyPrimary.getId() != null) {
            unique.putIfAbsent(legacyPrimary.getId(), BookAuthorResponse.fromEntity(legacyPrimary));
        }

        List<BookAuthorResponse> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(BookAuthorResponse::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static BookAuthorResponse resolvePrimaryAuthor(Book book, List<BookAuthorResponse> authors) {
        if (book.getAuthor() != null && book.getAuthor().getId() != null) {
            return BookAuthorResponse.fromEntity(book.getAuthor());
        }
        return authors.isEmpty() ? null : authors.get(0);
    }

    public record BookAuthorResponse(Long id, String name, boolean active) {
        public static BookAuthorResponse fromEntity(Author author) {
            return new BookAuthorResponse(author.getId(), author.getName(), author.isActive());
        }
    }
}
