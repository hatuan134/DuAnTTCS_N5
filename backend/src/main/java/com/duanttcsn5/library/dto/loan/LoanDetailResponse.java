package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

/** Read persisted lending data; due dates are never recalculated when viewing a loan. */
public record LoanDetailResponse(
        Long id, String loanNumber, Long reservationId,
        Long readerId, String readerName, Long createdById, String createdByName,
        OffsetDateTime borrowedAt, List<Item> items
) {
    public LoanDetailResponse { items = List.copyOf(items); }

    public record Item(Long id, Long copyId, String barcode, Long bookId, String bookTitle,
                       OffsetDateTime borrowedAt, OffsetDateTime dueAt,
                       OffsetDateTime returnedAt, Long returnedById, String returnedByName) {
        /** Preserve the constructors used by existing lending code and tests. */
        public Item(Long id, Long copyId, String barcode, Long bookId, String bookTitle,
                    OffsetDateTime borrowedAt, OffsetDateTime dueAt) {
            this(id, copyId, barcode, bookId, bookTitle, borrowedAt, dueAt, null, null, null);
        }
    }
}
