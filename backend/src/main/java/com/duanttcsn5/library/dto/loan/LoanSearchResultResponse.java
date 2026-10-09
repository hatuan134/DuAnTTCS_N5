package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

/** One result per loan; all historical copy items remain visible beneath that loan. */
public record LoanSearchResultResponse(
        Long id, String loanNumber, String cardNumber, String readerName,
        OffsetDateTime borrowedAt, String status, List<Item> items
) {
    public LoanSearchResultResponse {
        items = List.copyOf(items);
    }

    public record Item(String barcode, String bookTitle, OffsetDateTime dueAt, String status) {}
}
