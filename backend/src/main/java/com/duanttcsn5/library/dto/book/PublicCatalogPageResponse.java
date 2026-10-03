package com.duanttcsn5.library.dto.book;

import java.util.List;

public record PublicCatalogPageResponse(
        List<BookResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort
) {
}
