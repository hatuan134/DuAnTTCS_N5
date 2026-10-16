package com.duanttcsn5.library.dto.librarycard;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import com.duanttcsn5.library.repository.LibraryCardRepository.LockHistoryRow;

public record LibraryCardLockHistoryResponse(Long id, String cardNumber, String action,
        Long actorUserId, String actorName, OffsetDateTime performedAt, String reason,
        OffsetDateTime lockStartedAt, OffsetDateTime lockEndsAt, String lockReason,
        Long lockActorUserId, String lockActorName, String previousStatus, String resultingStatus) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
    }
    public static LibraryCardLockHistoryResponse fromRow(LockHistoryRow r) {
        return new LibraryCardLockHistoryResponse(r.getId(), r.getCardNumber(), r.getAction(),
                r.getActorUserId(), r.getActorName(), toOffset(r.getPerformedAt()), r.getReason(),
                toOffset(r.getLockStartedAt()), toOffset(r.getLockEndsAt()), r.getLockReason(), r.getLockActorUserId(),
                r.getLockActorName(), r.getPreviousStatus(), r.getResultingStatus());
    }
}
