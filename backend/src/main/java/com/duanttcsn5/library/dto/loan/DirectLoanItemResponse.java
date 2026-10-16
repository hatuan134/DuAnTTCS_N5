package com.duanttcsn5.library.dto.loan;

/** Preview only: this response does not create a loan or change copy status. */
public record DirectLoanItemResponse(
        Long bookCopyId, Long bookId, String barcode, String bookTitle, long remainingBooks
) {}
