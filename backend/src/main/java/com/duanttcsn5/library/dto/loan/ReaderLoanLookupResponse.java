package com.duanttcsn5.library.dto.loan;

import java.util.List;

/** Staff-only lookup; only the information needed to select a library card. */
public record ReaderLoanLookupResponse(List<Reader> items, int page, int size, long total) {
    public ReaderLoanLookupResponse { items = List.copyOf(items); }
    public record Reader(Long readerId, String readerName, String readerPhone, String cardNumber) {}
}
