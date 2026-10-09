package com.duanttcsn5.library.dto.loan;

import java.util.List;

/** Zero-based server pagination; a page holds at most 20 distinct loan records. */
public record LoanSearchPageResponse(
        List<LoanSearchResultResponse> items, int page, int size, long total
) {
    public LoanSearchPageResponse {
        items = List.copyOf(items);
    }
}
