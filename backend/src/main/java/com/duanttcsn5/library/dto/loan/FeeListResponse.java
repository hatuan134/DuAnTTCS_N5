package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

public record FeeListResponse(List<Item> items, int page, int size, long totalItems) {
    public record Item(Long id, FeeCollectionResponse.Reader reader, String feeType, String description,
            Long loanId, String loanNumber, long amountVnd, long paidAmountVnd, long waivedAmountVnd,
            long outstandingAmountVnd, String paymentStatus, OffsetDateTime createdAt, Long latestPaymentId) {}
}
