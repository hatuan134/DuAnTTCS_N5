package com.duanttcsn5.library.dto.loan;

import java.time.LocalDate;

/** A read-only snapshot; no loan or reservation is created by this check. */
public record ReaderLoanEligibilityResponse(
        Long readerId, String readerName, String cardNumber, String cardTypeName,
        String cardStatus, LocalDate expiresAt, int maxBooks, long borrowedBooks,
        long remainingBooks, boolean eligible, String reasonCode, String message
) {}
