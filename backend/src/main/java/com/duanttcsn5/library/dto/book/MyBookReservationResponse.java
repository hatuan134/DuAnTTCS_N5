package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record MyBookReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String status,
        OffsetDateTime reservedAt,
        Long queuePosition,
        OffsetDateTime pickupDeadline
) {}
