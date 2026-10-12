package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record FeeReductionRequestResponse(
        Long id, Long feeId, Long loanId, String loanNumber, String readerName,
        String bookTitle, String barcode, Long requestedBy, String requesterName,
        long originalAmountVnd, long requestedAmountVnd, long outstandingAmountVnd,
        String reason, OffsetDateTime submittedAt, String status) {}
