package com.duanttcsn5.library.dto.loan;

public record ReservationLoanContextResponse(
        String cardNumber, boolean converted, String loanNumber,
        LoanDatePreviewResponse dates, String dateError
) {
    public ReservationLoanContextResponse(String cardNumber, boolean converted, String loanNumber) {
        this(cardNumber, converted, loanNumber, null, null);
    }
}
