package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record ConfirmReturnResponse(
        String message, Long copyId, String barcode, String bookTitle,
        Long loanId, String loanNumber, Long itemId, String itemStatus, String loanStatus,
        String copyStatus, OffsetDateTime returnedAt, Long returnedById, String returnedByName) {
}
