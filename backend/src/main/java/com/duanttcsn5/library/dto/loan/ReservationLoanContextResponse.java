package com.duanttcsn5.library.dto.loan;

import com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse;
import java.time.OffsetDateTime;

public record ReservationLoanContextResponse(
        String cardNumber, boolean converted, String loanNumber,
        LoanDatePreviewResponse dates, String dateError,
        String status, boolean expired, OffsetDateTime pickupDeadline,
        OffsetDateTime checkedAt, String pickupMessage, String copyStatus,
        ReadyForPickupReservationResponse reservation
) {
    public ReservationLoanContextResponse(String cardNumber, boolean converted, String loanNumber,
                                          LoanDatePreviewResponse dates, String dateError) {
        this(cardNumber, converted, loanNumber, dates, dateError, null, false, null, null, null, null, null);
    }

    public ReservationLoanContextResponse(String cardNumber, boolean converted, String loanNumber) {
        this(cardNumber, converted, loanNumber, null, null);
    }
}
