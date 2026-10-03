package com.duanttcsn5.library.dto.book;

import java.time.OffsetDateTime;
import java.util.List;

/** Staff-only view of every reservation for one title, including its history. */
public record BookReservationQueueResponse(
        Long bookId,
        String bookTitle,
        List<QueueEntry> items
) {
    public record QueueEntry(
            Long id,
            Long readerId,
            String readerName,
            OffsetDateTime reservedAt,
            String status,
            Long queuePosition,
            Long copyId,
            String barcode
    ) {
    }
}
