package com.duanttcsn5.library.dto.book;

import java.util.List;

public record BookReservationBatchResponse(
        int requestedQuantity,
        int createdCount,
        long activeReservationCount,
        long remainingActiveSlots,
        List<BookReservationResponse> reservations,
        String message
) {
}
