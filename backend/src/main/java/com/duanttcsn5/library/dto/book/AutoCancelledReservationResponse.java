package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record AutoCancelledReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        Long readerId,
        String readerName,
        Long copyId,
        String barcode,
        String status,
        OffsetDateTime reservedAt,
        OffsetDateTime pickupDeadline,
        OffsetDateTime cancelledAt,
        String cancelledByName,
        String cancellationReason
) {}
