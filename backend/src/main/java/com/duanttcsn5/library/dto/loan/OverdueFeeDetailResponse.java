package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

public record OverdueFeeDetailResponse(
        Long id, Long loanId, String loanNumber, String readerName, String bookTitle,
        String barcode, long originalAmountVnd, long paidAmountVnd, long outstandingAmountVnd,
        OffsetDateTime returnedAt, FeeReductionRequestResponse pendingRequest,
        long waivedAmountVnd, List<FeeReductionRequestResponse> reductionRequests) {}
