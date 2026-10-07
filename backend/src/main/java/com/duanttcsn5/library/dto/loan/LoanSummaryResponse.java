package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;

public record LoanSummaryResponse(
        Long id, String loanNumber, Long readerId, String readerName,
        Long createdById, String createdByName, OffsetDateTime borrowedAt, long itemCount
) {}
