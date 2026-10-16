package com.duanttcsn5.library.dto.loan;

import java.util.List;

public record FeeReductionRequestPageResponse(
        List<FeeReductionRequestResponse> items, int page, int size, long totalItems, int totalPages) {
    public FeeReductionRequestPageResponse { items = List.copyOf(items); }
}
