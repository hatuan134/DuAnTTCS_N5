package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.auth.ForgotPasswordRequest;
import com.duanttcsn5.library.dto.auth.ForgotPasswordResponse;
import com.duanttcsn5.library.dto.auth.ResetPasswordRequest;
import com.duanttcsn5.library.dto.auth.ResetPasswordResponse;
import com.duanttcsn5.library.dto.auth.ValidateResetPasswordTokenResponse;
import com.duanttcsn5.library.entity.PasswordResetRequest;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PasswordResetRequestRepository;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final String RESET_PASSWORD_TYPE = "RESET_PASSWORD";
    private static final String NEUTRAL_SUCCESS_MESSAGE =
            "Nếu email của bạn tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đến hòm thư.";
    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetRequestRepository passwordResetRequestRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditLogService auditLogService,
            JdbcTemplate jdbcTemplate) {
        this.passwordResetRequestRepository = passwordResetRequestRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Tiếp nhận yêu cầu quên mật khẩu, kiểm tra rate limit và gửi email chứa liên kết 30 phút.
     * Luôn trả về thông báo trung tính để không làm lộ email có tồn tại trong hệ thống hay không (AC2).
     */
    @Transactional
    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Rate limit: tối đa 3 lần/email/giờ (AC - Ngăn gửi quá 3 lần/giờ)
        OffsetDateTime oneHourAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        long recentCount = passwordResetRequestRepository
                .countByRequestedEmailIgnoreCaseAndRequestTypeAndCreatedAtAfter(
                        email, RESET_PASSWORD_TYPE, oneHourAgo);

        if (recentCount >= MAX_REQUESTS_PER_HOUR) {
            // Đã đạt giới hạn 3 lần trong 1 giờ: từ chối tạo link, không gửi email, giữ thông báo trung tính
            return new ForgotPasswordResponse(NEUTRAL_SUCCESS_MESSAGE);
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Email không tồn tại trong hệ thống: không gửi email, trả thông báo trung tính (AC2)
            return new ForgotPasswordResponse(NEUTRAL_SUCCESS_MESSAGE);
        }

        User user = userOpt.get();
        // Tài khoản không ở trạng thái hoạt động bình thường cũng không gửi
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus()) && !"LOCKED".equalsIgnoreCase(user.getStatus())) {
            return new ForgotPasswordResponse(NEUTRAL_SUCCESS_MESSAGE);
        }

        // Sinh token ngẫu nhiên bảo mật cao (32 bytes = 64 ký tự Hex)
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);
        String tokenHash = sha256(rawToken);

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUser(user);
        resetRequest.setRequestedEmail(email);
        resetRequest.setTokenHash(tokenHash);
        resetRequest.setRequestType(RESET_PASSWORD_TYPE);
        resetRequest.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(TOKEN_EXPIRY_MINUTES));
        resetRequest.setRequestedIp(ipAddress);
        passwordResetRequestRepository.save(resetRequest);

        // Gửi email bất đồng bộ chứa liên kết đặt lại mật khẩu
        emailService.sendResetPasswordEmail(user.getEmail(), user.getFullName(), rawToken);

        // Ghi audit log
        auditLogService.logPasswordResetRequested(user.getId(), user.getEmail(), ipAddress);

        return new ForgotPasswordResponse(NEUTRAL_SUCCESS_MESSAGE);
    }

    /**
     * Xác thực liên kết đặt lại mật khẩu khi người dùng mở trang từ email.
     * Kiểm tra thời hạn 30 phút và trạng thái đã sử dụng (AC1).
     */
    @Transactional(readOnly = true)
    public ValidateResetPasswordTokenResponse validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TOKEN",
                    "Mã liên kết không hợp lệ.");
        }

        String tokenHash = sha256(rawToken.trim());
        PasswordResetRequest resetRequest = passwordResetRequestRepository
                .findByTokenHashAndRequestTypeWithUser(tokenHash, RESET_PASSWORD_TYPE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_TOKEN",
                        "Liên kết đặt lại mật khẩu không hợp lệ hoặc không tồn tại."));

        if (resetRequest.getUsedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_ALREADY_USED",
                    "Liên kết đặt lại mật khẩu đã được sử dụng. Vui lòng tạo yêu cầu mới nếu cần.");
        }

        if (resetRequest.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_EXPIRED",
                    "Liên kết đặt lại mật khẩu đã hết hạn (chỉ có hiệu lực trong vòng 30 phút).");
        }

        return new ValidateResetPasswordTokenResponse(
                true,
                maskEmail(resetRequest.getRequestedEmail()),
                resetRequest.getUser() != null ? resetRequest.getUser().getFullName() : null
        );
    }

    /**
     * Đặt lại mật khẩu mới, hủy toàn bộ phiên đăng nhập cũ (AC4) và đánh dấu liên kết đã sử dụng.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String ipAddress) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORDS_DO_NOT_MATCH",
                    "Mật khẩu xác nhận không khớp.");
        }

        String rawToken = request.token().trim();
        String tokenHash = sha256(rawToken);

        PasswordResetRequest resetRequest = passwordResetRequestRepository
                .findByTokenHashAndRequestTypeWithUser(tokenHash, RESET_PASSWORD_TYPE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_TOKEN",
                        "Liên kết đặt lại mật khẩu không hợp lệ hoặc không tồn tại."));

        if (resetRequest.getUsedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_ALREADY_USED",
                    "Liên kết đặt lại mật khẩu đã được sử dụng.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (resetRequest.getExpiresAt().isBefore(now)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOKEN_EXPIRED",
                    "Liên kết đặt lại mật khẩu đã hết hạn.");
        }

        User user = resetRequest.getUser();
        if (user == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "USER_NOT_FOUND",
                    "Không tìm thấy tài khoản người dùng gắn với liên kết.");
        }

        // Cập nhật mật khẩu mới
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(encodedPassword);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        // Vô hiệu hóa toàn bộ Access Token JWT cũ bằng cách tăng token_version (AC4)
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Thu hồi toàn bộ Refresh Token đang hoạt động của user (AC4)
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);

        // Đánh dấu token đã được sử dụng (chỉ dùng được một lần)
        resetRequest.setUsedAt(now);
        passwordResetRequestRepository.save(resetRequest);

        // Lưu lịch sử mật khẩu
        try {
            jdbcTemplate.update(
                    "INSERT INTO password_history (user_id, password_hash, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    user.getId(),
                    encodedPassword);
        } catch (Exception ignored) {
            // Không làm gián đoạn luồng chính nếu lưu lịch sử gặp lỗi phụ
        }

        // Ghi audit log
        auditLogService.logPasswordResetCompleted(user.getId(), user.getEmail(), ipAddress);

        return new ResetPasswordResponse(
                "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
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
