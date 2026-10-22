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

    public void expireStaleJobs() {
        jdbc.update("UPDATE permission_review_jobs SET state='FAILED',message='Quá thời gian chờ GitHub Actions (90 phút). Kiểm tra Actions và chạy lại.',finished_at=CURRENT_TIMESTAMP WHERE state IN ('STARTING','RUNNING') AND started_at < CURRENT_TIMESTAMP - INTERVAL '90 minutes'");
    }
    /** Serialize callback retries before testing/inserting the immutable receipt. */
    public Optional<String> lockCiReceipt(UUID key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 4092))", key.toString());
        return jdbc.query("SELECT body_sha256 FROM permission_review_ci_receipts WHERE run_key=?",
                (rs,n) -> rs.getString(1), key).stream().findFirst();
    }
    public void createCompletedCiJob(UUID key, String fingerprint) {
        // Terminal initial state avoids competing with the existing one-active-manual-job index.
        jdbc.update("""
                INSERT INTO permission_review_jobs(run_key,state,message,requested_by_name,fingerprint,finished_at)
                VALUES (?,'FAILED','Đã nhận callback CI; đang kiểm tra bằng chứng.','GitHub Actions',?,CURRENT_TIMESTAMP)
                ON CONFLICT (run_key) DO NOTHING
                """, key, fingerprint);
    }
    public void saveCiReceipt(UUID key, com.duanttcsn5.library.service.PermissionReviewGitHubCallbackService.CiRun ci, String hash) {
        jdbc.update("""
                INSERT INTO permission_review_ci_receipts(run_key,repository,workflow_run_id,run_attempt,commit_sha,
                    branch,event_name,started_at,completed_at,total_tests,failed_tests,skipped_tests,body_sha256)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, key,ci.repository(),ci.runId(),ci.attempt(),ci.commitSha(),ci.branch(),ci.event(),
                ci.startedAt(),ci.completedAt(),ci.total(),ci.failed(),ci.skipped(),hash);
        jdbc.update("UPDATE permission_review_jobs SET started_at=?,finished_at=? WHERE run_key=?",
                ci.startedAt(),ci.completedAt(),key);
    }
    public Optional<Long> lockPendingActor(UUID key, String fingerprint) {
        return jdbc.query("SELECT requested_by_user_id FROM permission_review_jobs WHERE run_key=? AND fingerprint=? AND state IN ('STARTING','RUNNING') AND finished_at IS NULL FOR UPDATE",
                (rs,n) -> (Long)rs.getObject(1), key,fingerprint).stream().findFirst();
    }
    public void createJob(UUID key, Long actor, String name, String fingerprint) {
        jdbc.update("INSERT INTO permission_review_jobs(run_key,state,message,requested_by_user_id,requested_by_name,fingerprint) VALUES (?,'STARTING','Đang chuẩn bị tác vụ kiểm thử trên môi trường cô lập.',?,?,?)", key, actor, name, fingerprint);
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
        return (rs,n) -> new RunState(rs.getObject("run_key",UUID.class),rs.getString("state"),rs.getString("message"),rs.getString("requested_by_name"),rs.getObject("started_at",OffsetDateTime.class),rs.getObject("finished_at",OffsetDateTime.class),rs.getObject("report_id",Long.class), ciSummary(rs.getObject("run_key",UUID.class)));
    }
    private CiSummary ciSummary(UUID key) {
        return jdbc.query("SELECT * FROM permission_review_ci_receipts WHERE run_key=?", (rs,n) ->
                new CiSummary(rs.getString("commit_sha"),rs.getString("branch"),rs.getString("event_name"),
                    "https://github.com/"+rs.getString("repository")+"/actions/runs/"+rs.getLong("workflow_run_id")+"/attempts/"+rs.getInt("run_attempt"),
                    rs.getInt("total_tests"),rs.getInt("failed_tests"),rs.getInt("skipped_tests")), key).stream().findFirst().orElse(null);
    }
}
