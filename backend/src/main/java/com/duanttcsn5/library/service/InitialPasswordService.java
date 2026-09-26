package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.account.SetInitialPasswordRequest;
import com.duanttcsn5.library.dto.account.ValidateInitialPasswordTokenResponse;
import com.duanttcsn5.library.entity.PasswordResetRequest;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PasswordResetRequestRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class InitialPasswordService {

    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    public InitialPasswordService(
            PasswordResetRequestRepository passwordResetRequestRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            JdbcTemplate jdbcTemplate) {
        this.passwordResetRequestRepository = passwordResetRequestRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ValidateInitialPasswordTokenResponse validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TOKEN",
                    "Liên kết thiết lập mật khẩu không hợp lệ.");
        }

        String tokenHash = sha256(rawToken.trim());
        PasswordResetRequest request = passwordResetRequestRepository
                .findByTokenHashAndRequestTypeWithUser(tokenHash, "INITIAL_PASSWORD")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_TOKEN",
                        "Liên kết thiết lập mật khẩu không hợp lệ hoặc không tồn tại."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (request.getUsedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_ALREADY_USED",
                    "Liên kết thiết lập mật khẩu này đã được sử dụng.");
        }

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_EXPIRED",
                    "Liên kết thiết lập mật khẩu đã hết hạn (quá 24 giờ).");
        }

        return new ValidateInitialPasswordTokenResponse(
                true,
                request.getUser().getEmail(),
                request.getUser().getFullName());
    }

    @Transactional
    public void setInitialPassword(SetInitialPasswordRequest requestDto, String ipAddress) {
        if (requestDto.token() == null || requestDto.token().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TOKEN",
                    "Liên kết thiết lập mật khẩu không hợp lệ.");
        }

        String tokenHash = sha256(requestDto.token().trim());
        PasswordResetRequest resetRequest = passwordResetRequestRepository
                .findByTokenHashAndRequestTypeWithUser(tokenHash, "INITIAL_PASSWORD")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_TOKEN",
                        "Liên kết thiết lập mật khẩu không hợp lệ hoặc không tồn tại."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (resetRequest.getUsedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_ALREADY_USED",
                    "Liên kết thiết lập mật khẩu này đã được sử dụng.");
        }

        if (resetRequest.getExpiresAt() != null && resetRequest.getExpiresAt().isBefore(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_EXPIRED",
                    "Liên kết thiết lập mật khẩu đã hết hạn (quá 24 giờ).");
        }

        User user = resetRequest.getUser();
        if ("LOCKED".equalsIgnoreCase(user.getStatus()) || "DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_LOCKED",
                    "Tài khoản hiện đang bị khóa. Vui lòng liên hệ Quản trị viên.");
        }

        String encodedPassword = passwordEncoder.encode(requestDto.password());
        user.setPasswordHash(encodedPassword);
        if ("PENDING".equals(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Đánh dấu token đã được sử dụng
        resetRequest.setUsedAt(now);
        passwordResetRequestRepository.save(resetRequest);

        // Lưu lịch sử mật khẩu
        try {
            jdbcTemplate.update(
                    "INSERT INTO password_history (user_id, password_hash, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    user.getId(),
                    encodedPassword);
        } catch (Exception ignored) {
            // Không làm gián đoạn luồng chính nếu lưu lịch sử gặp vấn đề phụ
        }

        // Ghi audit log
        auditLogService.logInitialPasswordSet(user.getId(), user.getEmail(), ipAddress);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }
}
