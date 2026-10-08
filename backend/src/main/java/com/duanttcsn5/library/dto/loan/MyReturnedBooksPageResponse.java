package com.duanttcsn5.library.dto.loan;

import java.util.List;

/** Zero-based server page, matching the existing paginated API convention. */
public record MyReturnedBooksPageResponse(
        List<MyReturnedBookResponse> items, int page, int size, long total) {}
