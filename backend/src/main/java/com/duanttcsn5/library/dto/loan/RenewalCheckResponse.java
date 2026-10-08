package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

/** Successful renewal returns both the persisted new due date and the updated loan quota. */
public record RenewalCheckResponse(boolean eligible, String message,
                                   int renewalsUsed, int maxRenewals, OffsetDateTime dueAt) {}
