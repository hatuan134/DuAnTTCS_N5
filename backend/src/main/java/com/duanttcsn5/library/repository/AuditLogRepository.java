package com.duanttcsn5.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long actorUserId,
                       String action,
                       String entityType,
                       String entityId,
                       String afterDataJson,
                       String ipAddress) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (
                    actor_user_id,
                    action,
                    entity_type,
                    entity_id,
                    after_data,
                    ip_address,
                    created_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS inet), CURRENT_TIMESTAMP)
                """,
                actorUserId,
                action,
                entityType,
                entityId,
                afterDataJson,
                normalizeIp(ipAddress));
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "127.0.0.1";
        }

        if ("0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return "::1";
        }

        return ipAddress;
    }
}
