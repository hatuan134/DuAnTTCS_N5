package com.duanttcsn5.library.dto.loan;

/** S3-05.3 records an allowed renewal count; the due date is not changed in this slice. */
public record RenewalCheckResponse(boolean eligible, String message,
                                   int renewalsUsed, int maxRenewals) {}
