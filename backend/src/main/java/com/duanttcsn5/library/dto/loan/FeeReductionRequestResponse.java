package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record FeeReductionRequestResponse(
        Long id, Long feeId, Long loanId, String loanNumber, String readerName,
        String bookTitle, String barcode, Long requestedBy, String requesterName,
        long originalAmountVnd, long requestedAmountVnd, long outstandingAmountVnd,
        String reason, OffsetDateTime submittedAt, String status,
        long paidAmountVnd, long approvedAmountVnd, Long decidedBy, String deciderName,
        OffsetDateTime decidedAt, String decisionReason) {
    public FeeReductionRequestResponse(Long id, Long feeId, Long loanId, String loanNumber, String readerName,
            String bookTitle, String barcode, Long requestedBy, String requesterName, long originalAmountVnd,
            long requestedAmountVnd, long outstandingAmountVnd, String reason, OffsetDateTime submittedAt,
            String status) {
        this(id, feeId, loanId, loanNumber, readerName, bookTitle, barcode, requestedBy, requesterName,
                originalAmountVnd, requestedAmountVnd, outstandingAmountVnd, reason, submittedAt, status,
                0, 0, null, null, null, null);
    }
}
