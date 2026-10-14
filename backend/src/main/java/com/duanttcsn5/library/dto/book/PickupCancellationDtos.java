package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public final class PickupCancellationDtos {
    private PickupCancellationDtos() {}
    public record Preview(long reservationId, long bookId, String bookTitle, String readerName,
                          String recipientEmail, String reservationStatus, OffsetDateTime pickupDeadline,
                          OffsetDateTime evaluatedAt, OffsetDateTime actualNow, boolean eligible,
                          boolean canRunNow, String reason, String subject, String emailContent) {}
    public record RunRequest(OffsetDateTime checkTime) {}
    public record RunResult(String message, ReadyPickupNotificationDtos.History history) {}
}
