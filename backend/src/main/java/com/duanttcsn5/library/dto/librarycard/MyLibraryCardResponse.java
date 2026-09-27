package com.duanttcsn5.library.dto.librarycard;

import java.time.LocalDate;

public record MyLibraryCardResponse(
        Long userId,
        String fullName,
        String memberCode,
        LocalDate dateOfBirth,
        String registrationStatus,
        String rejectionReason,
        String cardNumber,
        String cardTypeName,
        LocalDate issuedAt,
        LocalDate expiresAt,
        String cardStatus
) {
}
