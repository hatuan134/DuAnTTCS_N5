package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

/** One outstanding physical copy; remainingDays is null when a legacy due date is missing. */
public record MyBorrowedBookResponse(Long id, String bookTitle, String barcode,
        OffsetDateTime borrowedAt, OffsetDateTime dueAt, Long remainingDays) {}
