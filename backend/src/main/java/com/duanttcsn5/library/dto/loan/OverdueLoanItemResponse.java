package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

/** One unreturned loan item whose persisted due date is before today in the library timezone. */
public record OverdueLoanItemResponse(
        Long loanId,
        String loanNumber,
        Long itemId,
        Long readerId,
        String readerName,
        String readerPhone,
        Long bookId,
        String bookTitle,
        OffsetDateTime dueAt,
        long overdueDays
) {}
