package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;

public record BookReservationResponse(
        Long id,
        Long bookId,
        String status,
        OffsetDateTime reservedAt,
        Long queuePosition,
        String message,
        OffsetDateTime pickupDeadline,
        ReservedCopy reservedCopy
) {
    public record ReservedCopy(
            Long copyId,
            String barcode,
            String warehouseCode,
            String warehouseName,
            String shelfCode,
            String shelfName
    ) {
    }
}
