package com.duanttcsn5.library.dto.profile;

import java.time.LocalDate;

public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String address,
        LocalDate dateOfBirth,
        String memberCode,
        String role,
        String roleName,
        boolean hasCard,
        String cardNumber,
        String cardTypeName,
        String cardStatus,
        LocalDate cardIssuedAt,
        LocalDate cardExpiresAt
) {
}
