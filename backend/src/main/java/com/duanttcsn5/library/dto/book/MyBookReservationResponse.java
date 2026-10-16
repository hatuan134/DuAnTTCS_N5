package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record MyBookReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String status,
        OffsetDateTime reservedAt,
        Long queuePosition,
        OffsetDateTime pickupDeadline,
        String pickupLocation,
        String cancellationReason,
        OffsetDateTime cancelledAt,
        boolean cancelledBySystem
) {
    public MyBookReservationResponse(Long id, Long bookId, String bookTitle, String status,
            OffsetDateTime reservedAt, Long queuePosition, OffsetDateTime pickupDeadline, String pickupLocation) {
        this(id, bookId, bookTitle, status, reservedAt, queuePosition, pickupDeadline, pickupLocation, null, null, false);
    }
    public MyBookReservationResponse(Long id, Long bookId, String bookTitle, String status,
            OffsetDateTime reservedAt, Long queuePosition, OffsetDateTime pickupDeadline) {
        this(id, bookId, bookTitle, status, reservedAt, queuePosition, pickupDeadline, null);
    }
}
