package com.duanttcsn5.library.dto.reader;

import com.duanttcsn5.library.entity.ReaderProfile;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ReaderProfileResponse(
        Long userId,
        String fullName,
        String email,
        String phone,
        String address,
        String userStatus,
        String memberCode,
        LocalDate dateOfBirth,
        String cardTypeName,
        String registrationStatus,
        String rejectionReason,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        Long reviewedBy
) {
    public static ReaderProfileResponse fromEntity(ReaderProfile profile) {
        return fromEntity(profile, null);
    }

    public static ReaderProfileResponse fromEntity(ReaderProfile profile, String cardTypeName) {
        return new ReaderProfileResponse(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getUser().getEmail(),
                profile.getUser().getPhone(),
                profile.getUser().getAddress(),
                profile.getUser().getStatus(),
                profile.getMemberCode(),
                profile.getDateOfBirth(),
                cardTypeName,
                profile.getRegistrationStatus(),
                profile.getRejectionReason(),
                profile.getSubmittedAt(),
                profile.getReviewedAt(),
                profile.getReviewedBy()
        );
    }
}
