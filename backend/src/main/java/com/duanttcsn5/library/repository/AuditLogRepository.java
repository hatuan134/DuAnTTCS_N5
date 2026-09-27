package com.duanttcsn5.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public List<AuditLogRow> findByFilters(OffsetDateTime fromInclusive,
                                           OffsetDateTime toExclusive,
                                           Long actorId,
                                           List<String> actions,
                                           String keyword) {
        StringBuilder sql = new StringBuilder(baseSelect());
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();

        if (fromInclusive != null) {
            where.add("al.created_at >= ?");
            params.add(fromInclusive);
        }

        if (toExclusive != null) {
            where.add("al.created_at < ?");
            params.add(toExclusive);
        }

        if (actorId != null) {
            where.add("al.actor_user_id = ?");
            params.add(actorId);
        }

        if (actions != null && !actions.isEmpty()) {
            String placeholders = String.join(",", actions.stream().map(action -> "?").toList());
            where.add("al.action IN (" + placeholders + ")");
            params.addAll(actions);
        }

        if (keyword != null && !keyword.isBlank()) {
            where.add("""
                    (
                        LOWER(COALESCE(actor.full_name, '')) LIKE ?
                        OR LOWER(COALESCE(actor.email, '')) LIKE ?
                        OR LOWER(COALESCE(al.entity_type, '')) LIKE ?
                        OR LOWER(COALESCE(al.entity_id, '')) LIKE ?
                        OR LOWER(COALESCE(al.after_data::text, '')) LIKE ?
                        OR LOWER(COALESCE(host(al.ip_address), '')) LIKE ?
                    )
                    """);
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 6; i++) {
                params.add(pattern);
            }
        }

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        sql.append(" ORDER BY al.created_at DESC, al.id DESC LIMIT 1000");

        return jdbcTemplate.query(sql.toString(), rowMapper(), params.toArray());
    }

    public Optional<AuditLogRow> findById(Long id) {
        String sql = baseSelect() + " WHERE al.id = ?";
        List<AuditLogRow> rows = jdbcTemplate.query(sql, rowMapper(), id);
        return rows.stream().findFirst();
    }

    public List<AuditActorRow> findActors() {
        return jdbcTemplate.query("""
                SELECT DISTINCT
                    u.id,
                    u.full_name,
                    u.email,
                    r.name AS role_name
                FROM audit_logs al
                JOIN users u ON u.id = al.actor_user_id
                LEFT JOIN roles r ON r.id = u.role_id
                ORDER BY u.full_name, u.email
                """, (rs, rowNum) -> new AuditActorRow(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("role_name")
        ));
    }

    private String baseSelect() {
        return """
                SELECT
                    al.id,
                    al.actor_user_id,
                    actor.full_name AS actor_name,
                    actor.email AS actor_email,
                    actor_role.name AS actor_role,
                    al.action,
                    al.entity_type,
                    al.entity_id,
                    al.before_data::text AS before_data,
                    al.after_data::text AS after_data,
                    host(al.ip_address) AS ip_address,
                    al.created_at,
                    target_user.full_name AS target_user_name,
                    target_user.email AS target_user_email,
                    target_card_type.name AS target_card_type_name
                FROM audit_logs al
                LEFT JOIN users actor ON actor.id = al.actor_user_id
                LEFT JOIN roles actor_role ON actor_role.id = actor.role_id
                LEFT JOIN users target_user ON target_user.id = CASE
                    WHEN al.entity_type = 'USER' AND COALESCE(al.entity_id, '') ~ '^[0-9]+$'
                    THEN CAST(al.entity_id AS BIGINT)
                    ELSE NULL
                END
                LEFT JOIN card_types target_card_type ON target_card_type.id = CASE
                    WHEN al.entity_type = 'CARD_TYPE' AND COALESCE(al.entity_id, '') ~ '^[0-9]+$'
                    THEN CAST(al.entity_id AS BIGINT)
                    ELSE NULL
                END
                """;
    }

    private org.springframework.jdbc.core.RowMapper<AuditLogRow> rowMapper() {
        return (rs, rowNum) -> new AuditLogRow(
                rs.getLong("id"),
                rs.getObject("actor_user_id", Long.class),
                rs.getString("actor_name"),
                rs.getString("actor_email"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("before_data"),
                rs.getString("after_data"),
                rs.getString("ip_address"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("target_user_name"),
                rs.getString("target_user_email"),
                rs.getString("target_card_type_name")
        );
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

    public record AuditLogRow(
            Long id,
            Long actorUserId,
            String actorName,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String beforeData,
            String afterData,
            String ipAddress,
            OffsetDateTime createdAt,
            String targetUserName,
            String targetUserEmail,
            String targetCardTypeName
    ) {
    }

    public record AuditActorRow(
            Long id,
            String fullName,
            String email,
            String role
    ) {
    }
}
