package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record OverdueFeeDetailResponse(
        Long id, Long loanId, String loanNumber, String readerName, String bookTitle,
        String barcode, long originalAmountVnd, long paidAmountVnd, long outstandingAmountVnd,
        OffsetDateTime returnedAt, FeeReductionRequestResponse pendingRequest) {}
