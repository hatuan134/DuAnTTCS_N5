package com.duanttcsn5.library.dto.loan;

/** Both a first confirmation and a retry return the persisted loan and current quota. */
public record DirectLoanResponse(LoanDetailResponse loan, ReaderLoanEligibilityResponse reader, String message) {}
