package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.audit.AuditActionOptionResponse;
import com.duanttcsn5.library.dto.audit.AuditActorOptionResponse;
import com.duanttcsn5.library.dto.audit.AuditFilterOptionsResponse;
import com.duanttcsn5.library.dto.audit.AuditLogResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Map<String, List<String>> ACTION_GROUPS = Map.of(
            "LOGIN", List.of("LOGIN_SUCCESS", "LOGIN_FAILED", "ACCOUNT_TEMP_LOCKED"),
            "CREATE_ACCOUNT", List.of("USER_CREATED"),
            "UPDATE_ACCOUNT", List.of("USER_UPDATED", "USER_DELETED", "USER_STATUS_UPDATED", "INITIAL_PASSWORD_SET", "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_COMPLETED", "READER_CONTACT_UPDATED", "READER_PASSWORD_CHANGED"),
            "ISSUE_CARD", List.of("LIBRARY_CARD_ISSUED"),
            "UPDATE_POLICY", List.of(
                    "CARD_TYPE_CREATED",
                    "CARD_TYPE_UPDATED",
                    "CARD_TYPE_ACTIVATED",
                    "CARD_TYPE_DEACTIVATED",
                    "CARD_TYPE_DELETED")
    );

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(LocalDate fromDate,
                                         LocalDate toDate,
                                         Long actorId,
                                         String action,
                                         String keyword) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "Ngày bắt đầu không được sau ngày kết thúc.");
        }

        OffsetDateTime fromInclusive = fromDate == null
                ? null
                : fromDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();

        OffsetDateTime toExclusive = toDate == null
                ? null
                : toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();

        List<String> actionCodes = resolveActionCodes(action);

        return auditLogRepository
                .findByFilters(fromInclusive, toExclusive, actorId, actionCodes, keyword)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getById(Long id) {
        return auditLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "AUDIT_LOG_NOT_FOUND",
                        "Không tìm thấy nhật ký hoạt động."));
    }

    @Transactional(readOnly = true)
    public AuditFilterOptionsResponse getFilterOptions() {
        List<AuditActorOptionResponse> actors = auditLogRepository.findActors().stream()
                .map(row -> new AuditActorOptionResponse(
                        row.id(),
                        row.fullName(),
                        row.email(),
                        roleLabel(row.role())))
                .toList();

        List<AuditActionOptionResponse> actions = List.of(
                new AuditActionOptionResponse("LOGIN", "Đăng nhập"),
                new AuditActionOptionResponse("CREATE_ACCOUNT", "Tạo tài khoản"),
                new AuditActionOptionResponse("UPDATE_ACCOUNT", "Sửa tài khoản"),
                new AuditActionOptionResponse("ISSUE_CARD", "Cấp thẻ"),
                new AuditActionOptionResponse("UPDATE_POLICY", "Sửa chính sách mượn")
        );

        return new AuditFilterOptionsResponse(actors, actions);
    }

    public void logLoginFailed(Long userId, int failedAttempts, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "LOGIN_FAILED",
                "USER",
                userId.toString(),
                toJson(Map.of(
                        "failedAttempts", failedAttempts,
                        "reason", "INVALID_PASSWORD")),
                ipAddress);
    }

    public void logLoginFailedUnknownEmail(String email, String ipAddress) {
        auditLogRepository.insert(
                null,
                "LOGIN_FAILED",
                "LOGIN_ATTEMPT",
                email,
                toJson(Map.of(
                        "email", email,
                        "reason", "UNKNOWN_EMAIL")),
                ipAddress);
    }

    public void logLoginRejected(Long userId, String email, String reason, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "LOGIN_FAILED",
                "USER",
                userId.toString(),
                toJson(Map.of(
                        "email", email,
                        "reason", reason)),
                ipAddress);
    }

    public void logTemporaryLock(Long userId, String lockedUntil, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "ACCOUNT_TEMP_LOCKED",
                "USER",
                userId.toString(),
                toJson(Map.of("lockedUntil", lockedUntil)),
                ipAddress);
    }

    public void logLoginSuccess(Long userId, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "LOGIN_SUCCESS",
                "USER",
                userId.toString(),
                toJson(Map.of("result", "SUCCESS")),
                ipAddress);
    }

    public void logUserCreated(Long actorAdminId, Long createdUserId, String email, String role, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_CREATED",
                "USER",
                createdUserId.toString(),
                toJson(Map.of(
                        "email", email,
                        "role", role)),
                ipAddress);
    }

    public void logUserUpdated(Long actorAdminId, Long targetUserId, String oldEmail, String newEmail,
                               String oldRole, String newRole, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_UPDATED",
                "USER",
                targetUserId.toString(),
                toJson(Map.of(
                        "oldEmail", oldEmail,
                        "newEmail", newEmail,
                        "oldRole", oldRole,
                        "newRole", newRole)),
                ipAddress);
    }

    public void logUserDeleted(Long actorAdminId, Long targetUserId, String email, String oldStatus, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_DELETED",
                "USER",
                targetUserId.toString(),
                toJson(Map.of(
                        "email", email,
                        "oldStatus", oldStatus,
                        "newStatus", "DISABLED")),
                ipAddress);
    }

    public void logUserStatusUpdated(Long actorAdminId, Long targetUserId, String oldStatus, String newStatus, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_STATUS_UPDATED",
                "USER",
                targetUserId.toString(),
                toJson(Map.of(
                        "oldStatus", oldStatus,
                        "newStatus", newStatus)),
                ipAddress);
    }

    public void logInitialPasswordSet(Long userId, String email, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "INITIAL_PASSWORD_SET",
                "USER",
                userId.toString(),
                toJson(Map.of("email", email)),
                ipAddress);
    }

    public void logPasswordResetRequested(Long userId, String email, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "PASSWORD_RESET_REQUESTED",
                "USER",
                userId != null ? userId.toString() : email,
                toJson(Map.of("email", email)),
                ipAddress);
    }

    public void logPasswordResetCompleted(Long userId, String email, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "PASSWORD_RESET_COMPLETED",
                "USER",
                userId.toString(),
                toJson(Map.of("email", email)),
                ipAddress);
    }

    public void logReaderContactUpdated(Long userId,
                                        String oldEmail,
                                        String newEmail,
                                        boolean emailChanged,
                                        boolean phoneChanged,
                                        boolean addressChanged,
                                        String ipAddress) {
        auditLogRepository.insert(
                userId,
                "READER_CONTACT_UPDATED",
                "USER",
                userId.toString(),
                toJson(Map.of(
                        "oldEmail", oldEmail,
                        "newEmail", newEmail,
                        "emailChanged", emailChanged,
                        "phoneChanged", phoneChanged,
                        "addressChanged", addressChanged)),
                ipAddress);
    }

    public void logReaderPasswordChanged(Long userId, String email, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "READER_PASSWORD_CHANGED",
                "USER",
                userId.toString(),
                toJson(Map.of("email", email)),
                ipAddress);
    }

    /**
     * Điểm tích hợp dành cho S1-04. Khi chức năng cấp thẻ được triển khai,
     * service cấp thẻ chỉ cần gọi phương thức này sau khi lưu thẻ thành công.
     */
    public void logLibraryCardIssued(Long actorUserId,
                                     Long cardId,
                                     String cardNumber,
                                     Long readerUserId,
                                     String readerName,
                                     String cardTypeName,
                                     String expiresAt,
                                     String ipAddress) {
        auditLogRepository.insert(
                actorUserId,
                "LIBRARY_CARD_ISSUED",
                "LIBRARY_CARD",
                cardId.toString(),
                toJson(Map.of(
                        "cardNumber", cardNumber,
                        "readerUserId", readerUserId,
                        "readerName", readerName,
                        "cardTypeName", cardTypeName,
                        "expiresAt", expiresAt)),
                ipAddress);
    }

    private List<String> resolveActionCodes(String action) {
        if (action == null || action.isBlank() || "ALL".equalsIgnoreCase(action)) {
            return List.of();
        }

        String normalized = action.trim().toUpperCase();
        return ACTION_GROUPS.getOrDefault(normalized, List.of(normalized));
    }

    private AuditLogResponse toResponse(AuditLogRepository.AuditLogRow row) {
        JsonNode afterData = parseJson(row.afterData());
        JsonNode beforeData = parseJson(row.beforeData());

        String actionGroup = actionGroup(row.action());
        String actionLabel = actionLabel(row.action());
        String actor = resolveActor(row, afterData);
        String actorRole = resolveActorRole(row);
        String target = resolveTarget(row, afterData);
        String targetType = targetType(row.entityType());
        String detail = buildDetail(row.action(), afterData, beforeData);

        return new AuditLogResponse(
                row.id(),
                row.createdAt(),
                row.actorUserId(),
                actor,
                actorRole,
                row.action(),
                actionGroup,
                actionLabel,
                target,
                targetType,
                row.entityType(),
                row.entityId(),
                row.ipAddress() == null ? "-" : row.ipAddress(),
                detail
        );
    }

    private String resolveActor(AuditLogRepository.AuditLogRow row, JsonNode afterData) {
        if (row.actorName() != null && !row.actorName().isBlank()) {
            if (row.actorEmail() != null && !row.actorEmail().isBlank()) {
                return row.actorName() + " (" + row.actorEmail() + ")";
            }
            return row.actorName();
        }

        String email = text(afterData, "email");
        if (email != null) {
            return email + " (chưa xác thực)";
        }

        return "Không xác định";
    }

    private String resolveActorRole(AuditLogRepository.AuditLogRow row) {
        if (row.actorRole() != null && !row.actorRole().isBlank()) {
            return roleLabel(row.actorRole());
        }
        return "Chưa xác thực";
    }

    private String resolveTarget(AuditLogRepository.AuditLogRow row, JsonNode afterData) {
        if ("USER".equals(row.entityType())) {
            if (row.targetUserName() != null && !row.targetUserName().isBlank()) {
                String email = row.targetUserEmail();
                return email == null || email.isBlank()
                        ? row.targetUserName()
                        : row.targetUserName() + " (" + email + ")";
            }

            String email = text(afterData, "email");
            if (email != null) {
                return email;
            }
        }

        if ("LOGIN_ATTEMPT".equals(row.entityType())) {
            String email = text(afterData, "email");
            return email == null ? row.entityId() : email;
        }

        if ("CARD_TYPE".equals(row.entityType())) {
            if (row.targetCardTypeName() != null && !row.targetCardTypeName().isBlank()) {
                return row.targetCardTypeName();
            }
            String name = text(afterData, "name");
            if (name != null) {
                return name;
            }
        }

        if ("LIBRARY_CARD".equals(row.entityType())) {
            String cardNumber = text(afterData, "cardNumber");
            if (cardNumber != null) {
                return cardNumber;
            }
        }

        return row.entityId() == null || row.entityId().isBlank()
                ? targetType(row.entityType())
                : targetType(row.entityType()) + " #" + row.entityId();
    }

    private String targetType(String entityType) {
        if (entityType == null) {
            return "Hệ thống";
        }

        return switch (entityType) {
            case "USER" -> "Tài khoản người dùng";
            case "LOGIN_ATTEMPT" -> "Phiên đăng nhập";
            case "LIBRARY_CARD" -> "Thẻ thư viện";
            case "CARD_TYPE" -> "Chính sách mượn";
            case "READER_PROFILE" -> "Hồ sơ bạn đọc";
            case "WAREHOUSE" -> "Kho";
            case "SHELF" -> "Kệ";
            case "LIBRARY_WEEKLY_SCHEDULE" -> "Lịch làm việc";
            case "LIBRARY_CLOSED_DATE" -> "Ngày đóng cửa";
            default -> entityType;
        };
    }

    private String actionGroup(String action) {
        if (ACTION_GROUPS.get("LOGIN").contains(action)) return "LOGIN";
        if (ACTION_GROUPS.get("CREATE_ACCOUNT").contains(action)) return "CREATE_ACCOUNT";
        if (ACTION_GROUPS.get("UPDATE_ACCOUNT").contains(action)) return "UPDATE_ACCOUNT";
        if (ACTION_GROUPS.get("ISSUE_CARD").contains(action)) return "ISSUE_CARD";
        if (ACTION_GROUPS.get("UPDATE_POLICY").contains(action)) return "UPDATE_POLICY";
        return "OTHER";
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "LOGIN_SUCCESS" -> "Đăng nhập thành công";
            case "LOGIN_FAILED" -> "Đăng nhập thất bại";
            case "ACCOUNT_TEMP_LOCKED" -> "Khóa tạm tài khoản";
            case "USER_CREATED" -> "Tạo tài khoản";
            case "USER_UPDATED" -> "Cập nhật tài khoản";
            case "USER_DELETED" -> "Xóa tài khoản";
            case "USER_STATUS_UPDATED" -> "Sửa trạng thái tài khoản";
            case "INITIAL_PASSWORD_SET" -> "Thiết lập mật khẩu lần đầu";
            case "PASSWORD_RESET_REQUESTED" -> "Yêu cầu đặt lại mật khẩu";
            case "PASSWORD_RESET_COMPLETED" -> "Đặt lại mật khẩu thành công";
            case "READER_CONTACT_UPDATED" -> "Cập nhật thông tin liên hệ";
            case "READER_PASSWORD_CHANGED" -> "Đổi mật khẩu";
            case "LIBRARY_CARD_ISSUED" -> "Cấp thẻ thư viện";
            case "READER_APPLICATION_REJECTED" -> "Từ chối hồ sơ bạn đọc";
            case "CARD_TYPE_CREATED" -> "Tạo chính sách mượn";
            case "CARD_TYPE_UPDATED" -> "Sửa chính sách mượn";
            case "CARD_TYPE_ACTIVATED" -> "Áp dụng chính sách mượn";
            case "CARD_TYPE_DEACTIVATED" -> "Ngừng chính sách mượn";
            case "CARD_TYPE_DELETED" -> "Xóa chính sách mượn";
            case "READER_REGISTERED" -> "Đăng ký bạn đọc";
            case "WAREHOUSE_CREATED" -> "Tạo kho";
            case "WAREHOUSE_UPDATED" -> "Sửa kho";
            case "SHELF_CREATED" -> "Tạo kệ";
            case "SHELF_UPDATED" -> "Sửa kệ";
            case "SHELF_DELETED" -> "Xóa kệ";
            case "WEEKLY_SCHEDULE_UPDATED" -> "Sửa lịch làm việc";
            case "CLOSED_DATE_CREATED" -> "Tạo ngày đóng cửa";
            case "CLOSED_DATE_UPDATED" -> "Sửa ngày đóng cửa";
            case "CLOSED_DATE_DELETED" -> "Xóa ngày đóng cửa";
            default -> action;
        };
    }

    private String buildDetail(String action, JsonNode afterData, JsonNode beforeData) {
        return switch (action) {
            case "LOGIN_SUCCESS" -> "Đăng nhập thành công vào hệ thống.";
            case "LOGIN_FAILED" -> loginFailedDetail(afterData);
            case "ACCOUNT_TEMP_LOCKED" -> "Tài khoản bị khóa tạm đến " + valueOrDash(afterData, "lockedUntil") + ".";
            case "USER_CREATED" -> "Tạo tài khoản " + valueOrDash(afterData, "email")
                    + " với vai trò " + roleLabel(valueOrDash(afterData, "role")) + ".";
            case "USER_UPDATED" -> "Cập nhật tài khoản từ " + valueOrDash(afterData, "oldEmail")
                    + " sang " + valueOrDash(afterData, "newEmail")
                    + ", vai trò từ " + roleLabel(valueOrDash(afterData, "oldRole"))
                    + " sang " + roleLabel(valueOrDash(afterData, "newRole")) + ".";
            case "USER_DELETED" -> "Xóa tài khoản " + valueOrDash(afterData, "email")
                    + " khỏi danh sách quản lý và chuyển sang trạng thái Ngừng hoạt động.";
            case "USER_STATUS_UPDATED" -> "Cập nhật trạng thái tài khoản từ "
                    + statusLabel(valueOrDash(afterData, "oldStatus")) + " sang "
                    + statusLabel(valueOrDash(afterData, "newStatus")) + ".";
            case "INITIAL_PASSWORD_SET" -> "Người dùng đã thiết lập mật khẩu lần đầu.";
            case "PASSWORD_RESET_REQUESTED" -> "Yêu cầu đặt lại mật khẩu cho tài khoản " + valueOrDash(afterData, "email") + ".";
            case "PASSWORD_RESET_COMPLETED" -> "Đặt lại mật khẩu thành công cho tài khoản " + valueOrDash(afterData, "email") + ".";
            case "READER_CONTACT_UPDATED" -> "Bạn đọc cập nhật thông tin liên hệ."
                    + (afterData != null && afterData.path("emailChanged").asBoolean(false)
                    ? " Email đổi từ " + valueOrDash(afterData, "oldEmail") + " sang " + valueOrDash(afterData, "newEmail") + "."
                    : " Email không thay đổi.");
            case "READER_PASSWORD_CHANGED" -> "Bạn đọc đổi mật khẩu tài khoản thành công.";
            case "LIBRARY_CARD_ISSUED" -> "Cấp thẻ " + valueOrDash(afterData, "cardNumber")
                    + " cho " + valueOrDash(afterData, "readerName")
                    + ", loại " + valueOrDash(afterData, "cardTypeName")
                    + ", hạn đến " + valueOrDash(afterData, "expiresAt") + ".";
            case "READER_APPLICATION_REJECTED" -> "Từ chối hồ sơ của "
                    + valueOrDash(afterData, "readerName")
                    + ". Lý do: " + valueOrDash(afterData, "reason") + ".";
            case "CARD_TYPE_CREATED", "CARD_TYPE_UPDATED", "CARD_TYPE_ACTIVATED", "CARD_TYPE_DEACTIVATED", "CARD_TYPE_DELETED" -> {
                String explicit = text(afterData, "action");
                String before = text(afterData, "before");
                String after = text(afterData, "after");
                String policy = text(afterData, "policy");
                if (before != null || after != null) {
                    yield (explicit == null ? actionLabel(action) : explicit)
                            + ". Trước: " + (before == null ? "-" : before)
                            + ". Sau: " + (after == null ? "-" : after) + ".";
                }
                yield (explicit == null ? actionLabel(action) : explicit)
                        + (policy == null ? "." : ": " + policy + ".");
            }
            default -> {
                if (afterData != null && !afterData.isNull() && afterData.size() > 0) {
                    yield afterData.toString();
                }
                if (beforeData != null && !beforeData.isNull() && beforeData.size() > 0) {
                    yield beforeData.toString();
                }
                yield actionLabel(action) + ".";
            }
        };
    }

    private String loginFailedDetail(JsonNode data) {
        String reason = text(data, "reason");
        if ("UNKNOWN_EMAIL".equals(reason)) {
            return "Đăng nhập thất bại với email không tồn tại trong hệ thống.";
        }
        if ("ACCOUNT_INACTIVE_OR_PASSWORD_NOT_SET".equals(reason)) {
            return "Đăng nhập thất bại vì tài khoản chưa sẵn sàng hoặc không hoạt động.";
        }
        if ("ACCOUNT_TEMPORARILY_LOCKED".equals(reason)) {
            return "Đăng nhập thất bại vì tài khoản đang bị khóa tạm.";
        }

        JsonNode attempts = data == null ? null : data.get("failedAttempts");
        if (attempts != null && attempts.isNumber()) {
            return "Đăng nhập thất bại. Số lần nhập sai liên tiếp: " + attempts.asInt() + ".";
        }
        return "Đăng nhập thất bại.";
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "ADMIN" -> "Quản trị hệ thống";
            case "LIBRARY_MANAGER" -> "Quản lý thư viện";
            case "LIBRARIAN" -> "Thủ thư";
            case "READER" -> "Bạn đọc";
            default -> role;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "ACTIVE" -> "Đang hoạt động";
            case "LOCKED" -> "Đã khóa";
            case "DISABLED" -> "Ngừng hoạt động";
            default -> status;
        };
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            return objectMapper.getNodeFactory().nullNode();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String valueOrDash(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? "-" : value;
    }

    private String toJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Không thể tạo dữ liệu nhật ký.", exception);
        }
    }
}
