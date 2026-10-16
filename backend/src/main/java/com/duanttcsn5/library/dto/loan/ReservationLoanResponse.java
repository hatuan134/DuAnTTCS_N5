package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record ReservationLoanResponse(
        Long id, String loanNumber, Long reservationId,
        Long readerId, String readerName, String cardNumber,
        Long bookId, String bookTitle, Long copyId, String barcode,
        OffsetDateTime borrowedAt, String message, LoanDatePreviewResponse dates
) {
    public ReservationLoanResponse(Long id, String loanNumber, Long reservationId,
            Long readerId, String readerName, String cardNumber,
            Long bookId, String bookTitle, Long copyId, String barcode,
            OffsetDateTime borrowedAt, String message) {
        this(id, loanNumber, reservationId, readerId, readerName, cardNumber,
                bookId, bookTitle, copyId, barcode, borrowedAt, message, null);
    }
}
