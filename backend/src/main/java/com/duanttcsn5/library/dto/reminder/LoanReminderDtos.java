package com.duanttcsn5.library.dto.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class LoanReminderDtos {

    public record LoanReminderItemDto(
            Long itemId,
            Long loanId,
            String loanNumber,
            Long bookId,
            String bookTitle,
            String copyBarcode,
            OffsetDateTime dueDate,
            long daysOverdue,
            long estimatedFeeVnd
    ) {}

    public record LoanReminderGroupDto(
            Long readerId,
            String readerName,
            String recipientEmail,
            String reminderGroup,
            List<LoanReminderItemDto> items,
            String subject,
            String emailContentHtml,
            boolean alreadySentToday
    ) {}

    public record LoanReminderPreviewResponse(
            LocalDate targetDate,
            int totalRecipients,
            int totalItems,
            List<LoanReminderGroupDto> reminders
    ) {}

    public record LoanReminderTriggerRequest(
            LocalDate targetDate
    ) {}

    public record LoanReminderExecutionResult(
            LocalDate date,
            int totalRecipients,
            int sentCount,
            int failedCount,
            int skippedCount,
            int totalItemsCount,
            String triggeredBy
    ) {}

    public record LoanReminderLogResponse(
            Long id,
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            String loanNumbers,
            String bookTitles,
            String itemsDetail,
            String subject,
            String emailContent,
            String status,
            String errorMessage,
            String triggeredBy,
            String triggeredByUserName,
            boolean isDryRun,
            OffsetDateTime sentAt
    ) {}
}
