package com.duanttcsn5.library.dto.librarycard;

import com.duanttcsn5.library.entity.LibraryCard;
import java.time.OffsetDateTime;

public record LibraryCardLockResponse(boolean active, String reason,
        OffsetDateTime startedAt, OffsetDateTime endsAt, Long actorUserId) {
    public static LibraryCardLockResponse fromEntity(LibraryCard card, OffsetDateTime now) {
        if (card == null || card.getManualLockStartedAt() == null) return null;
        return new LibraryCardLockResponse(card.hasActiveManualLock(now), card.getManualLockReason(),
                card.getManualLockStartedAt(), card.getManualLockEndsAt(), card.getManualLockActorId());
    }
}
