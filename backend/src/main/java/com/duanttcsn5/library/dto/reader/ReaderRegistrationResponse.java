package com.duanttcsn5.library.dto.reader;

import java.time.OffsetDateTime;

public record ReaderRegistrationResponse(
        Long userId,
        String fullName,
        String email,
        String memberCode,
        String registrationStatus,
        OffsetDateTime submittedAt,
        String message
) {
}
