package com.duanttcsn5.library.dto.loan;

public record ReservationLoanContextResponse(
        String cardNumber, boolean converted, String loanNumber
) {}
