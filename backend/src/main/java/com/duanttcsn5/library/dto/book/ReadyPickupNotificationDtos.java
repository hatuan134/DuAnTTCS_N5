package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;
import java.util.List;

public final class ReadyPickupNotificationDtos {
    private ReadyPickupNotificationDtos() {}
    public record Attempt(long id, int attemptNumber, OffsetDateTime startedAt,
                          OffsetDateTime finishedAt, String outcome, String errorCode,
                          String errorMessage, boolean simulated, Long actorId) {}
    public record Notification(long id, long reservationId, String notificationType,
                               String recipientEmail, String readerName, String bookTitle,
                               OffsetDateTime pickupDeadline, String pickupLocation,
                               OffsetDateTime eventAt, OffsetDateTime sendBefore,
                               OffsetDateTime sentAt, OffsetDateTime completedAt, String status,
                               int attemptCount, String subject, String emailContent,
                               String errorCode, String errorMessage, List<Attempt> attempts,
                               Long bookId, String cancellationReason, OffsetDateTime cancelledAt) {
        public Notification(long id, long reservationId, String notificationType, String recipientEmail,
                String readerName, String bookTitle, OffsetDateTime pickupDeadline, String pickupLocation,
                OffsetDateTime eventAt, OffsetDateTime sendBefore, OffsetDateTime sentAt, OffsetDateTime completedAt,
                String status, int attemptCount, String subject, String emailContent,
                String errorCode, String errorMessage, List<Attempt> attempts) {
            this(id, reservationId, notificationType, recipientEmail, readerName, bookTitle, pickupDeadline,
                    pickupLocation, eventAt, sendBefore, sentAt, completedAt, status, attemptCount,
                    subject, emailContent, errorCode, errorMessage, attempts, null, null, null);
        }
    }
    public record History(long reservationId, String reservationStatus, String pickupLocation,
                          String timeZone, List<Notification> notifications) {}
    public record TriggerRequest(Boolean simulateFailure) {}
    public record TriggerResult(String message, History history) {}
}
