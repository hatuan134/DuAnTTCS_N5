package com.duanttcsn5.library.dto.loan;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Saved snapshots, not live borrower/card values. */
public record LoanRejectionResponse(
        Long id, OffsetDateTime occurredAt, String source,
        Long readerId, String readerName, String cardNumber,
        Long actorId, String actorName, Long reservationId,
        long borrowedBooks, int maxBooks, long overdueLoans,
        BigDecimal unpaidAmountVnd, List<Reason> reasons,
        String eventType, String overrideReason, Long loanId
) {
    public LoanRejectionResponse(Long id, OffsetDateTime occurredAt, String source,
            Long readerId, String readerName, String cardNumber,
            Long actorId, String actorName, Long reservationId,
            long borrowedBooks, int maxBooks, long overdueLoans,
            BigDecimal unpaidAmountVnd, List<Reason> reasons) {
        this(id, occurredAt, source, readerId, readerName, cardNumber,
                actorId, actorName, reservationId, borrowedBooks, maxBooks,
                overdueLoans, unpaidAmountVnd, reasons, "BLOCKED", null, null);
    }
    public record Reason(String code, String message) {}
}
