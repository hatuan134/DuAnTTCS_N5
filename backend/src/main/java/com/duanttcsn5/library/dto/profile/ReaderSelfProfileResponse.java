package com.duanttcsn5.library.dto.profile;

import java.time.LocalDate;

public record ReaderSelfProfileResponse(
        Long userId,
        String fullName,
        LocalDate dateOfBirth,
        String memberCode,
        String email,
        String phone,
        String address,
        String registrationStatus,
        String rejectionReason,
        String cardNumber,
        String cardTypeName,
        LocalDate issuedAt,
        LocalDate expiresAt,
        String cardStatus
) {
}
