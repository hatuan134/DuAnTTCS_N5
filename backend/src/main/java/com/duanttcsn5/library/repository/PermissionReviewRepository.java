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

    public void createJob(UUID key, Long actor, String name, String fingerprint) {
        jdbc.update("INSERT INTO permission_review_jobs(run_key,state,message,requested_by_user_id,requested_by_name,fingerprint) VALUES (?,'STARTING','Đang chuẩn bị database kiểm thử riêng.',?,?,?)", key, actor, name, fingerprint);
    }
    public Optional<RunState> job(UUID key) {
        return jdbc.query("SELECT * FROM permission_review_jobs WHERE run_key=?", runMapper(), key).stream().findFirst();
    }
    public Optional<RunState> latestJob() {
        return jdbc.query("SELECT * FROM permission_review_jobs ORDER BY started_at DESC LIMIT 1", runMapper()).stream().findFirst();
    }
    public void running(UUID key) {
        jdbc.update("UPDATE permission_review_jobs SET state='RUNNING',message='Đang kiểm thử API bằng bốn vai trò và người chưa đăng nhập.' WHERE run_key=?", key);
    }
    public void finishJob(UUID key, String state, String message, Long reportId) {
        jdbc.update("UPDATE permission_review_jobs SET state=?,message=?,report_id=?,finished_at=CURRENT_TIMESTAMP WHERE run_key=?", state, message, reportId, key);
    }
    public void cleanupWarning(UUID key) {
        jdbc.update("UPDATE permission_review_jobs SET message=message || ' Chưa xóa được database thử; xem hướng dẫn dọn đúng database của lần chạy.' WHERE run_key=?",key);
    }
    public boolean serverExecuted(Long reportId, UUID runKey, String fingerprint) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM permission_review_jobs WHERE report_id=? AND run_key=? AND fingerprint=? AND state='SUCCEEDED')", Boolean.class, reportId, runKey, fingerprint));
    }
    public void recoverInterruptedJobs() {
        jdbc.update("UPDATE permission_review_jobs SET state='FAILED',message='Máy chủ đã khởi động lại trước khi ghi nhận kết quả. Vui lòng chạy lại.',finished_at=CURRENT_TIMESTAMP WHERE state IN ('STARTING','RUNNING')");
    }
    public Optional<Completion> completion(Long reportId) {
        return jdbc.query("SELECT * FROM permission_review_completions WHERE report_id=?", (rs,n) -> new Completion(rs.getLong("report_id"),rs.getString("confirmed_by_name"),rs.getObject("confirmed_at",OffsetDateTime.class),rs.getString("fingerprint")), reportId).stream().findFirst();
    }
    public void complete(Long reportId, Long actor, String name, String fingerprint, int cases) {
        jdbc.update("INSERT INTO permission_review_completions(report_id,confirmed_by_user_id,confirmed_by_name,fingerprint,role_case_count) VALUES (?,?,?,?,?)",reportId,actor,name,fingerprint,cases);
    }
    public void lockReport(Long id) {
        jdbc.queryForList("SELECT id FROM permission_review_runs WHERE id=? FOR UPDATE",id);
    }
    public List<ReportSummary> sprintReports(String policyVersion) {
        return jdbc.query("SELECT * FROM permission_review_runs WHERE policy_version LIKE 'S4-09.%' ORDER BY imported_at,id",summaryMapper());
    }
    private RowMapper<RunState> runMapper() {
        return (rs,n) -> new RunState(rs.getObject("run_key",UUID.class),rs.getString("state"),rs.getString("message"),rs.getString("requested_by_name"),rs.getObject("started_at",OffsetDateTime.class),rs.getObject("finished_at",OffsetDateTime.class),rs.getObject("report_id",Long.class));
    }
}
