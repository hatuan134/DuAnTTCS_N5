package com.duanttcsn5.library.dto.cardtype;

import com.duanttcsn5.library.entity.CardType;

import java.time.OffsetDateTime;

public record CardTypeResponse(
        Long id,
        String name,
        String description,
        Integer duration,
        int maxBooks,
        int loanDays,
        int maxRenewals,
        int renewalDays,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long usageCount
) {
    public static CardTypeResponse fromEntity(CardType entity, long usageCount) {
        return new CardTypeResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDurationMonths(),
                entity.getMaxBooks(),
                entity.getLoanDays(),
                entity.getMaxRenewals(),
                entity.getRenewalDays(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                usageCount
        );
    }
}
