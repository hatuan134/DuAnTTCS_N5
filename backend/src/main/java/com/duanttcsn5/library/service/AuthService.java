package com.duanttcsn5.library.service;

import com.duanttcsn5.library.config.AuthProperties;
import com.duanttcsn5.library.dto.auth.AuthResponse;
import com.duanttcsn5.library.dto.auth.AuthUserResponse;
import com.duanttcsn5.library.dto.auth.LoginRequest;
import com.duanttcsn5.library.entity.RefreshToken;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Email hoặc mật khẩu không chính xác.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthProperties authProperties,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.auditLogService = auditLogService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        User user = userRepository.findForLogin(email).orElse(null);

        if (user == null) {
            throw invalidCredentials();
        }

        if (!"ACTIVE".equals(user.getStatus()) || user.getPasswordHash() == null) {
            throw invalidCredentials();
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                throw new ApiException(
                        HttpStatus.LOCKED,
                        "ACCOUNT_TEMPORARILY_LOCKED",
                        "Tài khoản đang bị khóa tạm. Vui lòng thử lại sau.");
            }

            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int nextFailedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(nextFailedAttempts);

            auditLogService.logLoginFailed(user.getId(), nextFailedAttempts, ipAddress);

            if (nextFailedAttempts >= authProperties.getMaxFailedAttempts()) {
                OffsetDateTime lockedUntil = now.plusMinutes(authProperties.getLockMinutes());
                user.setLockedUntil(lockedUntil);

                auditLogService.logTemporaryLock(
                        user.getId(),
                        lockedUntil.toString(),
                        ipAddress);

                throw new ApiException(
                        HttpStatus.LOCKED,
                        "ACCOUNT_TEMPORARILY_LOCKED",
                        "Đăng nhập không thành công. Tài khoản đã bị khóa tạm trong 15 phút.");
            }

            throw invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        AuthResponse response = createSession(user);
        auditLogService.logLoginSuccess(user.getId(), ipAddress);
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tokenHash = sha256(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHashWithUser(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        if (storedToken.getRevokedAt() != null || !storedToken.getExpiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }

        User user = storedToken.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            throw invalidRefreshToken();
        }

        storedToken.setRevokedAt(now);
        return createSession(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."));

        return toUserResponse(user);
    }

    private AuthResponse createSession(User user) {
        JwtService.AccessToken accessToken = jwtService.issueAccessToken(user);

        String rawRefreshToken = generateRefreshToken();
        OffsetDateTime refreshExpiresAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusDays(authProperties.getRefreshTokenDays());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(refreshExpiresAt);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefreshToken,
                refreshExpiresAt,
                toUserResponse(user));
    }

    private AuthUserResponse toUserResponse(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getCode());
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                INVALID_CREDENTIALS_MESSAGE);
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "Refresh token không hợp lệ hoặc đã hết hạn.");
    }
}
