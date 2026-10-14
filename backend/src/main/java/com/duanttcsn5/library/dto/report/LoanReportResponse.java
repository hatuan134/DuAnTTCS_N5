package com.duanttcsn5.library.dto.report;

import java.time.LocalDate;
import java.util.List;

/** Values are counted per borrowed/returned copy; open and overdue are counted per loan slip. */
public record LoanReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalBorrowed,
        long totalReturned,
        long openLoans,
        long overdueLoans,
        double overdueRate,
        List<TopBorrowedBook> topBooks
) {
    public record TopBorrowedBook(long bookId, String bookTitle, long borrowCount, long stockCount) {}
}
