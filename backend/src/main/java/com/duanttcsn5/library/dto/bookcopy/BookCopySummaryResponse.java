package com.duanttcsn5.library.dto.bookcopy;

import java.util.List;

public record BookCopySummaryResponse(
        List<BookCopyResponse> copies,
        long availableCount,
        long originalCount,
        long totalCount
) {}
