package com.duanttcsn5.library.dto.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record LoanDatePreviewResponse(
        LocalDate borrowDate, String cardTypeName, int loanDays,
        LocalDate originalDueDate, LocalDate dueDate, OffsetDateTime dueAt,
        boolean adjusted, List<LocalDate> skippedClosedDates
) {}
