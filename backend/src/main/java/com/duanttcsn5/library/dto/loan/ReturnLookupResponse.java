package com.duanttcsn5.library.dto.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Read-only preview: no return, copy status or reservation is changed. */
public record ReturnLookupResponse(
        String status, String message, Long copyId, String barcode, String bookTitle,
        Long loanId, String loanNumber, Long itemId, Long readerId, String readerName,
        OffsetDateTime borrowedAt, OffsetDateTime dueAt, LocalDate checkedOn, Long overdueDays) {
}
