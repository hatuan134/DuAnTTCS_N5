package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class PermissionReviewRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PermissionReviewRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Long insert(ImportReport report, List<CaseResult> results, Long actorId, String actorName) {
        int detected = (int) results.stream().filter(result -> "FIXED".equals(result.outcome()) || !result.passed()).count();
        int fixed = (int) results.stream().filter(result -> "FIXED".equals(result.outcome())).count();
        int failed = (int) results.stream().filter(result -> !result.passed()).count();
        return jdbc.queryForObject("""
                INSERT INTO permission_review_runs(run_key, kind, environment, policy_version, fingerprint,
                    source_reference, executed_by, imported_by_user_id, imported_by_name, completed_at,
                    case_count, detected_count, fixed_count, failed_count, evidence, results)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb)) RETURNING id
                """, Long.class, report.runKey(), report.kind(), report.environment(), report.policyVersion(),
                report.fingerprint(), report.sourceReference(), report.executedBy(), actorId, actorName,
                report.completedAt(), results.size(), detected, fixed, failed,
                mapper.writeValueAsString(report), mapper.writeValueAsString(results));
    }

    public ReportPage list(int page, int size) {
        List<ReportSummary> items = jdbc.query("SELECT * FROM permission_review_runs ORDER BY imported_at DESC, id DESC LIMIT ? OFFSET ?",
                summaryMapper(), size, (long) page * size);
        Long count = jdbc.queryForObject("SELECT count(*) FROM permission_review_runs", Long.class);
        return new ReportPage(items, page, size, count == null ? 0 : count);
    }

    public Optional<ReportDetail> find(Long id, String fingerprint) {
        return jdbc.query("SELECT * FROM permission_review_runs WHERE id = ?", (rs, rowNum) -> {
            ImportReport report = mapper.readValue(rs.getString("evidence"), ImportReport.class);
            CaseResult[] results = mapper.readValue(rs.getString("results"), CaseResult[].class);
            return new ReportDetail(summaryMapper().mapRow(rs, rowNum), report, List.of(results),
                    fingerprint.equals(report.fingerprint()));
        }, id).stream().findFirst();
    }

    private RowMapper<ReportSummary> summaryMapper() {
        return (rs, rowNum) -> new ReportSummary(rs.getLong("id"), rs.getObject("run_key", UUID.class),
                rs.getString("kind"), rs.getString("environment"), rs.getString("source_reference"),
                rs.getString("executed_by"), rs.getString("imported_by_name"),
                rs.getObject("imported_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getInt("case_count"), rs.getInt("detected_count"), rs.getInt("fixed_count"), rs.getInt("failed_count"));
    }
}
