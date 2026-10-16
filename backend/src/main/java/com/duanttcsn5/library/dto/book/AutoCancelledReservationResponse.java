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
        String cancellationReason,
        String copyOutcome,
        Long nextReservationId,
        String nextReaderName,
        OffsetDateTime nextPickupDeadline
) {
    public AutoCancelledReservationResponse(
            Long id, Long bookId, String bookTitle, Long readerId, String readerName,
            Long copyId, String barcode, String status, OffsetDateTime reservedAt,
            OffsetDateTime pickupDeadline, OffsetDateTime cancelledAt,
            String cancelledByName, String cancellationReason) {
        this(id, bookId, bookTitle, readerId, readerName, copyId, barcode, status,
                reservedAt, pickupDeadline, cancelledAt, cancelledByName, cancellationReason,
                null, null, null, null);
    }
}
