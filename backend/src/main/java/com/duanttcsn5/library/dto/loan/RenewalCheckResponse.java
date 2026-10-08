package com.duanttcsn5.library.dto.loan;

/** Eligibility check only: no due date is calculated or updated in S3-05.1. */
public record RenewalCheckResponse(boolean eligible, String message) {}
