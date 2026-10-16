package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

/** Minimal staff view; never serializes the reader's account or credentials. */
public record ReadyForPickupReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        Long copyId,
        String barcode,
        Long readerId,
        String readerName,
        String status,
        OffsetDateTime reservedAt,
        OffsetDateTime pickupDeadline
) {
}
