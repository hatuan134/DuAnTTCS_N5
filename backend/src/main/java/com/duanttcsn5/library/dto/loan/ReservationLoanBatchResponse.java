package com.duanttcsn5.library.dto.loan;

import java.util.List;

/** Every item refers to the same saved loan header. */
public record ReservationLoanBatchResponse(
        Long id, String loanNumber, String message, List<ReservationLoanResponse> items
) {
    public ReservationLoanBatchResponse {
        items = List.copyOf(items);
    }
}
