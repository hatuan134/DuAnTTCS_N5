package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.account.AccountResponse;
import com.duanttcsn5.library.dto.account.CreateAccountRequest;
import com.duanttcsn5.library.entity.PasswordResetRequest;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PasswordResetRequestRepository;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserManagementService {

    private static final Set<String> ALLOWED_STAFF_ROLES = Set.of(
            "ADMIN",
            "LIBRARY_MANAGER",
            "LIBRARIAN"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetRequestRepository passwordResetRequestRepository,
            EmailService emailService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetRequestRepository = passwordResetRequestRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, Long adminId, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL",
                    "Email '" + email + "' đã tồn tại trong hệ thống.");
        }

        String roleCode = request.role().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STAFF_ROLES.contains(roleCode)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROLE",
                    "Vai trò không hợp lệ. Chỉ chấp nhận Quản trị hệ thống, Quản lý thư viện hoặc Thủ thư.");
        }

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ROLE_NOT_FOUND",
                        "Không tìm thấy vai trò tương ứng trong hệ thống."));

        String status = (request.status() != null && !request.status().isBlank())
                ? request.status().trim().toUpperCase(Locale.ROOT)
                : "ACTIVE";

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone() != null ? request.phone().trim() : null);
        user.setRole(role);
        user.setStatus(status);
        user.setPasswordHash(null); // Chưa có mật khẩu ban đầu
        user.setFailedLoginAttempts(0);
        user.setTokenVersion(0);

        User savedUser = userRepository.save(user);

        // Tạo token thiết lập mật khẩu lần đầu (24 giờ)
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);
        String tokenHash = sha256(rawToken);

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUser(savedUser);
        resetRequest.setRequestedEmail(savedUser.getEmail());
        resetRequest.setTokenHash(tokenHash);
        resetRequest.setRequestType("INITIAL_PASSWORD");
        resetRequest.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        passwordResetRequestRepository.save(resetRequest);

        // Gửi email hướng dẫn thiết lập mật khẩu
        emailService.sendInitialPasswordEmail(savedUser.getEmail(), savedUser.getFullName(), rawToken);

        // Ghi audit log
        auditLogService.logUserCreated(adminId, savedUser.getId(), savedUser.getEmail(), role.getCode(), ipAddress);

        return toAccountResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(String search, String role, String status) {
        List<User> users = userRepository.findAllWithRole();

        return users.stream()
                .filter(u -> u.getRole() != null && ALLOWED_STAFF_ROLES.contains(u.getRole().getCode()))
                .filter(u -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase(Locale.ROOT);
                        boolean matchesName = u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(s);
                        boolean matchesEmail = u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(s);
                        boolean matchesPhone = u.getPhone() != null && u.getPhone().toLowerCase(Locale.ROOT).contains(s);
                        if (!matchesName && !matchesEmail && !matchesPhone) {
                            return false;
                        }
                    }
                    if (role != null && !role.isBlank()) {
                        if (!u.getRole().getCode().equalsIgnoreCase(role.trim())) {
                            return false;
                        }
                    }
                    if (status != null && !status.isBlank()) {
                        if (!u.getStatus().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AccountResponse updateAccountStatus(Long userId, String newStatus, Long adminId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản người dùng."));

        String normalizedStatus = newStatus.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalizedStatus) && !"LOCKED".equals(normalizedStatus)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Trạng thái không hợp lệ. Chỉ chấp nhận ACTIVE hoặc LOCKED.");
        }

        if (adminId != null && adminId.equals(userId) && !"ACTIVE".equals(normalizedStatus)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_LOCK_SELF",
                    "Không thể tự khóa tài khoản của chính mình.");
        }

        String oldStatus = user.getStatus();
        if (oldStatus.equals(normalizedStatus)) {
            return toAccountResponse(user);
        }

        if ("LOCKED".equals(normalizedStatus)) {
            // Tăng tokenVersion để lập tức làm vô hiệu hóa các Access Token JWT hiện tại
            user.setTokenVersion(user.getTokenVersion() + 1);

            // Thu hồi toàn bộ Refresh Token đang hoạt động
            refreshTokenRepository.revokeAllActiveByUserId(userId, OffsetDateTime.now(ZoneOffset.UTC));
        } else if ("ACTIVE".equals(normalizedStatus)) {
            // Mở khóa: reset số lần thử và thời gian khóa tạm
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        user.setStatus(normalizedStatus);
        User savedUser = userRepository.save(user);

        // Ghi audit log
        auditLogService.logUserStatusUpdated(adminId, userId, oldStatus, normalizedStatus, ipAddress);

        return toAccountResponse(savedUser);
    }

    public AccountResponse toAccountResponse(User user) {
        String roleName = switch (user.getRole().getCode()) {
            case "ADMIN" -> "Quản trị hệ thống";
            case "LIBRARY_MANAGER" -> "Quản lý thư viện";
            case "LIBRARIAN" -> "Thủ thư";
            case "READER" -> "Bạn đọc";
            default -> user.getRole().getName();
        };

        return new AccountResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getCode(),
                roleName,
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
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
