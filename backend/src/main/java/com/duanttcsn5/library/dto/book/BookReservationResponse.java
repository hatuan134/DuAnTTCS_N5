package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record BookReservationResponse(
        Long id,
        Long bookId,
        String status,
        OffsetDateTime reservedAt,
        long queuePosition,
        String message
) {
}
