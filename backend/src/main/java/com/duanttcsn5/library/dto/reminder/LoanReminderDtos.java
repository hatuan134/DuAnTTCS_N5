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
            String readerCardNumber,
            String recipientEmail,
            String reminderGroup,
            List<LoanReminderItemDto> items,
            long totalEstimatedFeeVnd,
            String subject,
            String emailContentHtml,
            boolean alreadySentToday,
            String deliveryState
    ) {
        public LoanReminderGroupDto(Long readerId, String readerName, String readerCardNumber, String recipientEmail,
                                    String reminderGroup, List<LoanReminderItemDto> items, long totalEstimatedFeeVnd,
                                    String subject, String emailContentHtml, boolean alreadySentToday) {
            this(readerId, readerName, readerCardNumber, recipientEmail, reminderGroup, items, totalEstimatedFeeVnd,
                    subject, emailContentHtml, alreadySentToday, alreadySentToday ? "SENT" : "READY");
        }
        public LoanReminderGroupDto(Long readerId, String readerName, String recipientEmail, String reminderGroup,
                                    List<LoanReminderItemDto> items, long totalEstimatedFeeVnd, String subject,
                                    String emailContentHtml, boolean alreadySentToday) {
            this(readerId, readerName, null, recipientEmail, reminderGroup, items, totalEstimatedFeeVnd,
                    subject, emailContentHtml, alreadySentToday);
        }

        public LoanReminderGroupDto(Long readerId, String readerName, String recipientEmail, String reminderGroup,
                                    List<LoanReminderItemDto> items, String subject, String emailContentHtml,
                                    boolean alreadySentToday) {
            this(readerId, readerName, null, recipientEmail, reminderGroup, items,
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
            LocalDate targetDate,
            Boolean simulateFailure
    ) {
        public LoanReminderTriggerRequest(LocalDate targetDate) {
            this(targetDate, false);
        }
    }

    public record LoanReminderRetryRequest(
            Long logId,
            Boolean simulateFailure
    ) {}

    public record LoanReminderRetryExecutionResult(
            int totalProcessed,
            int succeededCount,
            int stillPendingCount,
            int maxRetriesReachedCount,
            int reviewRequiredCount,
            int permanentlyFailedCount,
            int skippedCount
    ) {
        public LoanReminderRetryExecutionResult(int total, int sent, int pending, int exhausted) {
            this(total, sent, pending, exhausted, 0, 0, 0);
        }
    }

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

    public record LoanReminderRetryAttemptDto(
            Long id,
            Long reminderLogId,
            int attemptNumber,
            OffsetDateTime attemptedAt,
            String status,
            String errorMessage,
            String triggeredBy
    ) {}

    public record LoanReminderLogResponse(
            Long id,
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String readerCardNumber,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            long totalEstimatedFeeVnd,
            int retryCount,
            int maxRetries,
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
            OffsetDateTime sentAt,
            List<LoanReminderRetryAttemptDto> attempts,
            String deliveryState
    ) {
        public LoanReminderLogResponse(Long id, LocalDate reminderDate, Long readerId, String readerName,
                String readerCardNumber, String recipientEmail, String reminderGroup, int loanItemCount,
                long totalEstimatedFeeVnd, int retryCount, int maxRetries, String loanNumbers, String bookTitles,
                String itemsDetail, String subject, String emailContent, String status, String errorMessage,
                String triggeredBy, String triggeredByUserName, boolean isDryRun, OffsetDateTime sentAt,
                List<LoanReminderRetryAttemptDto> attempts) {
            this(id, reminderDate, readerId, readerName, readerCardNumber, recipientEmail, reminderGroup,
                    loanItemCount, totalEstimatedFeeVnd, retryCount, maxRetries, loanNumbers, bookTitles, itemsDetail,
                    subject, emailContent, status, errorMessage, triggeredBy, triggeredByUserName, isDryRun, sentAt,
                    attempts, status);
        }
        public LoanReminderLogResponse(
                Long id, LocalDate reminderDate, Long readerId, String readerName,
                String recipientEmail, String reminderGroup, int loanItemCount,
                long totalEstimatedFeeVnd, String loanNumbers, String bookTitles,
                String itemsDetail, String subject, String emailContent, String status,
                String errorMessage, String triggeredBy, String triggeredByUserName,
                boolean isDryRun, OffsetDateTime sentAt
        ) {
            this(id, reminderDate, readerId, readerName, null, recipientEmail, reminderGroup,
                    loanItemCount, totalEstimatedFeeVnd, 0, 3, loanNumbers, bookTitles, itemsDetail,
                    subject, emailContent, status, errorMessage, triggeredBy, triggeredByUserName,
                    isDryRun, sentAt, List.of());
        }
    }
}
