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
        String pickupLocation
) {
    public MyBookReservationResponse(Long id, Long bookId, String bookTitle, String status,
            OffsetDateTime reservedAt, Long queuePosition, OffsetDateTime pickupDeadline) {
        this(id, bookId, bookTitle, status, reservedAt, queuePosition, pickupDeadline, null);
    }
}
