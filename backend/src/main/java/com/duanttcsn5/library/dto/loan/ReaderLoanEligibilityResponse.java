package com.duanttcsn5.library.dto.loan;

import java.time.LocalDate;
import java.util.List;

/** Read-only eligibility snapshot, not a saved loan or reservation. */
public record ReaderLoanEligibilityResponse(
        Long readerId, String readerName, String cardNumber, String cardTypeName,
        String cardStatus, LocalDate expiresAt, int maxBooks, long borrowedBooks,
        long remainingBooks, boolean eligible, String reasonCode, String message,
        List<BlockReason> blockReasons
) {
    /** Each issue can be shown separately while preserving reasonCode/message for old clients. */
    public record BlockReason(String code, String message) {}

    public ReaderLoanEligibilityResponse {
        blockReasons = blockReasons == null ? List.of() : List.copyOf(blockReasons);
    }

    /** Compatibility for existing callers of the S3-02.1 response contract. */
    public ReaderLoanEligibilityResponse(Long readerId, String readerName, String cardNumber, String cardTypeName,
            String cardStatus, LocalDate expiresAt, int maxBooks, long borrowedBooks,
            long remainingBooks, boolean eligible, String reasonCode, String message) {
        this(readerId, readerName, cardNumber, cardTypeName, cardStatus, expiresAt,
                maxBooks, borrowedBooks, remainingBooks, eligible, reasonCode, message, List.of());
    }
}
