package com.duanttcsn5.library.dto.book;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class PickupLastDayReminderDtos {
    private PickupLastDayReminderDtos() {}
    public record RunRequest(@NotNull(message = "Vui lòng chọn ngày chạy lịch nhắc.") LocalDate date) {}
    public record Entry(long reservationId, String readerName, String recipientEmail,
                        String bookTitle, OffsetDateTime pickupDeadline, String pickupLocation,
                        boolean eligible, String reason, String notificationStatus,
                        String subject, String emailContent) {}
    public record Report(LocalDate date, OffsetDateTime evaluatedAt, String sendTime,
                         String timeZone, boolean preview, int queuedCount,
                         int eligibleCount, String message, List<Entry> entries) {}
}
