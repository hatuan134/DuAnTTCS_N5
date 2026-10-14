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
            long openOverdueDays,
            long estimatedFeeVnd
    ) {
        public LoanReminderItemDto(Long itemId, Long loanId, String loanNumber, Long bookId, String bookTitle,
                                   String copyBarcode, OffsetDateTime dueDate, long daysOverdue, long estimatedFeeVnd) {
            this(itemId, loanId, loanNumber, bookId, bookTitle, copyBarcode, dueDate, daysOverdue, daysOverdue, estimatedFeeVnd);
        }
    }

    public record LoanReminderGroupDto(
            Long readerId,
            String readerName,
            String recipientEmail,
            String reminderGroup,
            List<LoanReminderItemDto> items,
            long totalEstimatedFeeVnd,
            String subject,
            String emailContentHtml,
            boolean alreadySentToday
    ) {
        public LoanReminderGroupDto(Long readerId, String readerName, String recipientEmail, String reminderGroup,
                                    List<LoanReminderItemDto> items, String subject, String emailContentHtml,
                                    boolean alreadySentToday) {
            this(readerId, readerName, recipientEmail, reminderGroup, items,
                    items != null ? items.stream().mapToLong(LoanReminderItemDto::estimatedFeeVnd).sum() : 0L,
                    subject, emailContentHtml, alreadySentToday);
        }
    }

    public record LoanReminderPreviewResponse(
            LocalDate targetDate,
            int totalRecipients,
            int totalItems,
            long grandTotalEstimatedFeeVnd,
            List<LoanReminderGroupDto> reminders
    ) {
        public LoanReminderPreviewResponse(LocalDate targetDate, int totalRecipients, int totalItems, List<LoanReminderGroupDto> reminders) {
            this(targetDate, totalRecipients, totalItems,
                    reminders != null ? reminders.stream().mapToLong(LoanReminderGroupDto::totalEstimatedFeeVnd).sum() : 0L,
                    reminders);
        }
    }

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
            long grandTotalEstimatedFeeVnd,
            String triggeredBy
    ) {
        public LoanReminderExecutionResult(LocalDate date, int totalRecipients, int sentCount, int failedCount,
                                           int skippedCount, int totalItemsCount, String triggeredBy) {
            this(date, totalRecipients, sentCount, failedCount, skippedCount, totalItemsCount, 0L, triggeredBy);
        }
    }

    public record LoanReminderLogResponse(
            Long id,
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            long totalEstimatedFeeVnd,
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
