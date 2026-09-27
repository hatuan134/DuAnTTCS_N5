package com.duanttcsn5.library.dto.librarycard;

import com.duanttcsn5.library.entity.ReaderProfile;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PendingReaderApplicationResponse(
        Long userId,
        String fullName,
        String memberCode,
        LocalDate dateOfBirth,
        String email,
        String phone,
        String address,
        OffsetDateTime submittedAt
) {
    public static PendingReaderApplicationResponse fromEntity(ReaderProfile profile) {
        return new PendingReaderApplicationResponse(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getMemberCode(),
                profile.getDateOfBirth(),
                profile.getUser().getEmail(),
                profile.getUser().getPhone(),
                profile.getUser().getAddress(),
                profile.getSubmittedAt()
        );
    }
}
