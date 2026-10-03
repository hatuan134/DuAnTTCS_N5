package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record CancelBookReservationResponse(
        Long id, Long bookId, String status,
        ReservationCancellationAuditResponse cancellation,
        Long copyId, String barcode, String copyOutcome,
        Long nextReservationId, String nextReaderName, OffsetDateTime pickupDeadline,
        String message
) {}
