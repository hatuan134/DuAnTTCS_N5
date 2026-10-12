package com.duanttcsn5.library.dto.librarycard;

import com.duanttcsn5.library.entity.LibraryCard;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LibraryCardResponse(
        Long id,
        String cardNumber,
        Long userId,
        String readerName,
        String memberCode,
        Long cardTypeId,
        String cardTypeName,
        LocalDate issuedAt,
        LocalDate expiresAt,
        String status,
        OffsetDateTime createdAt
) {
    public static LibraryCardResponse fromEntity(LibraryCard card, String memberCode) {
        return fromEntity(card, memberCode, card.getStatus());
    }

    public static LibraryCardResponse fromEntity(LibraryCard card, String memberCode, String status) {
        return new LibraryCardResponse(
                card.getId(),
                card.getCardNumber(),
                card.getUser().getId(),
                card.getUser().getFullName(),
                memberCode,
                card.getCardType().getId(),
                card.getCardType().getName(),
                card.getIssuedAt(),
                card.getExpiresAt(),
                status,
                card.getCreatedAt()
        );
    }
}
