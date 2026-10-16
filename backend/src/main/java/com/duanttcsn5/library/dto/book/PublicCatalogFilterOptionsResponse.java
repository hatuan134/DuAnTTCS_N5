package com.duanttcsn5.library.dto.book;

import java.util.List;

public record PublicCatalogFilterOptionsResponse(
        List<CategoryOption> categories,
        List<Integer> publicationYears
) {
    public record CategoryOption(Long id, String name) {
    }
}
