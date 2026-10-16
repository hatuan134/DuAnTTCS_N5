package com.duanttcsn5.library.dto.auth;

import java.time.OffsetDateTime;

public record AuthResponse(
        String tokenType,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt,
        AuthUserResponse user
) {
}
