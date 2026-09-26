package com.duanttcsn5.library.service;

import com.duanttcsn5.library.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logLoginFailed(Long userId, int failedAttempts, String ipAddress) {
        auditLogRepository.insert(
                null,
                "LOGIN_FAILED",
                "USER",
                userId.toString(),
                "{\"failedAttempts\":" + failedAttempts + "}",
                ipAddress);
    }

    public void logTemporaryLock(Long userId, String lockedUntil, String ipAddress) {
        auditLogRepository.insert(
                null,
                "ACCOUNT_TEMP_LOCKED",
                "USER",
                userId.toString(),
                "{\"lockedUntil\":\"" + lockedUntil + "\"}",
                ipAddress);
    }

    public void logLoginSuccess(Long userId, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "LOGIN_SUCCESS",
                "USER",
                userId.toString(),
                "{\"result\":\"SUCCESS\"}",
                ipAddress);
    }

    public void logUserCreated(Long actorAdminId, Long createdUserId, String email, String role, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_CREATED",
                "USER",
                createdUserId.toString(),
                "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}",
                ipAddress);
    }

    public void logUserStatusUpdated(Long actorAdminId, Long targetUserId, String oldStatus, String newStatus, String ipAddress) {
        auditLogRepository.insert(
                actorAdminId,
                "USER_STATUS_UPDATED",
                "USER",
                targetUserId.toString(),
                "{\"oldStatus\":\"" + oldStatus + "\",\"newStatus\":\"" + newStatus + "\"}",
                ipAddress);
    }

    public void logInitialPasswordSet(Long userId, String email, String ipAddress) {
        auditLogRepository.insert(
                userId,
                "INITIAL_PASSWORD_SET",
                "USER",
                userId.toString(),
                "{\"email\":\"" + email + "\"}",
                ipAddress);
    }
}

