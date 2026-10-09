package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record ConfirmReturnResponse(
        String message, Long copyId, String barcode, String bookTitle,
        Long loanId, String loanNumber, Long itemId, String itemStatus, String loanStatus,
        String copyStatus, OffsetDateTime returnedAt, Long returnedById, String returnedByName,
        Long nextReservationId, String nextReaderName,
        OffsetDateTime holdStartedAt, OffsetDateTime pickupDeadline) {

    /** Keep existing callers and tests compatible with a return that has no queue allocation. */
    public ConfirmReturnResponse(
            String message, Long copyId, String barcode, String bookTitle,
            Long loanId, String loanNumber, Long itemId, String itemStatus, String loanStatus,
            String copyStatus, OffsetDateTime returnedAt, Long returnedById, String returnedByName) {
        this(message, copyId, barcode, bookTitle, loanId, loanNumber, itemId, itemStatus, loanStatus,
                copyStatus, returnedAt, returnedById, returnedByName, null, null, null, null);
    }
}
