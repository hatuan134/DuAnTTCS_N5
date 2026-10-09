package com.duanttcsn5.library.dto.loan;

import java.util.List;

/** Zero-based server pagination; a page holds at most 20 distinct loan records. */
public record LoanSearchPageResponse(
        List<LoanSearchResultResponse> items, int page, int size, long total,
        String emptyReason
) {
    /** Preserve S3-08.1/2/3 response construction for other callers and existing tests. */
    public LoanSearchPageResponse(List<LoanSearchResultResponse> items, int page, int size, long total) {
        this(items, page, size, total, null);
    }

    public LoanSearchPageResponse {
        items = List.copyOf(items);
    }
}
