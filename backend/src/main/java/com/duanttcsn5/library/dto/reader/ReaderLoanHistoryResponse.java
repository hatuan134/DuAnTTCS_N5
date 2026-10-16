package com.duanttcsn5.library.dto.reader;

import java.time.OffsetDateTime;
import java.util.List;

/** S3-10.1: one persisted loan is one borrowing, including multi-copy loans. */
public record ReaderLoanHistoryResponse(
        ReaderProfileResponse profile,
        long openLoanCount,
        long totalBorrowCount,
        long lateReturnCount,
        List<Loan> loans
) {
    public ReaderLoanHistoryResponse { loans = List.copyOf(loans); }

    public record Loan(Long id, String loanNumber, OffsetDateTime borrowedAt,
                       String status, boolean returnedLate, List<Item> items) {
        public Loan { items = List.copyOf(items); }
    }

    public record Item(Long id, String bookTitle, String barcode,
                       OffsetDateTime borrowedAt, OffsetDateTime dueAt,
                       OffsetDateTime returnedAt, String status, boolean returnedLate) {}
}
