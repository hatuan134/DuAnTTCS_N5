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
}
