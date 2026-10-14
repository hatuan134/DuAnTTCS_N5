package com.duanttcsn5.library.dto.librarycard;

import java.time.OffsetDateTime;
import com.duanttcsn5.library.repository.LibraryCardRepository.LockHistoryRow;

public record LibraryCardLockHistoryResponse(Long id, String cardNumber, String action,
        Long actorUserId, String actorName, OffsetDateTime performedAt, String reason,
        OffsetDateTime lockStartedAt, OffsetDateTime lockEndsAt, String lockReason,
        Long lockActorUserId, String lockActorName, String previousStatus, String resultingStatus) {
    public static LibraryCardLockHistoryResponse fromRow(LockHistoryRow r) {
        return new LibraryCardLockHistoryResponse(r.getId(), r.getCardNumber(), r.getAction(),
                r.getActorUserId(), r.getActorName(), r.getPerformedAt(), r.getReason(),
                r.getLockStartedAt(), r.getLockEndsAt(), r.getLockReason(), r.getLockActorUserId(),
                r.getLockActorName(), r.getPreviousStatus(), r.getResultingStatus());
    }
}
