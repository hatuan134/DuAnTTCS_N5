package com.duanttcsn5.library.security;

import com.duanttcsn5.library.config.AuthProperties;
import com.duanttcsn5.library.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class JwtService {

    private final AuthProperties authProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;

        String configuredSecret = authProperties.getJwtSecretBase64();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu biến môi trường JWT_SECRET_BASE64. Hãy cấu hình secret Base64 tối thiểu 32 bytes.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 không phải chuỗi Base64 hợp lệ.", exception);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 phải giải mã thành tối thiểu 32 bytes.");
        }

        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        ImmutableSecret<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);

        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
        this.jwtDecoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public AccessToken issueAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(authProperties.getAccessTokenMinutes() * 60L);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("library-management-backend")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("role", user.getRole().getCode())
                .claim("tokenVersion", user.getTokenVersion())
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();

        return new AccessToken(
                token,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public record AccessToken(String value, OffsetDateTime expiresAt) {
    }
}
