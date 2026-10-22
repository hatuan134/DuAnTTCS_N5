package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.ImportReport;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Signed one-time callback from GitHub Actions, never from a browser account. */
@Service
public class PermissionReviewGitHubCallbackService {
    public record CiRun(String repository, long runId, int attempt, String commitSha, String branch,
            String event, java.time.OffsetDateTime startedAt, java.time.OffsetDateTime completedAt,
            int total, int failed, int skipped) {}
    public record Callback(UUID runKey, String fingerprint, boolean passed, ImportReport report, CiRun ci) {}
    private final GitHubPermissionReviewGateway gateway;
    private final PermissionReviewRepository repository;
    private final PermissionReviewService reviews;
    private final ObjectMapper mapper;
    private final Validator validator;

    public PermissionReviewGitHubCallbackService(GitHubPermissionReviewGateway gateway,
            PermissionReviewRepository repository, PermissionReviewService reviews,
            ObjectMapper mapper, Validator validator) {
        this.gateway = gateway; this.repository = repository; this.reviews = reviews;
        this.mapper = mapper; this.validator = validator;
    }

    public void verify(String timestamp, String signature, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024 || gateway.callbackSecret().length() < 32
                || timestamp == null || !timestamp.matches("[0-9]{10,13}")
                || signature == null || !signature.matches("sha256=[a-f0-9]{64}")) throw forbidden();
        try {
            long epoch = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - epoch) > 300) throw forbidden();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(gateway.callbackSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            byte[] sent = HexFormat.of().parseHex(signature.substring(7));
            if (!MessageDigest.isEqual(digest, sent)) throw forbidden();
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw forbidden(); }
    }

    @Transactional
    public void accept(String timestamp, String signature, String body) {
        verify(timestamp, signature, body);
        Callback message;
        try { message = mapper.readValue(body, Callback.class); }
        catch (Exception ex) { throw invalid(); }
        if (message == null || message.runKey() == null || message.fingerprint() == null
                || !message.fingerprint().matches("[a-f0-9]{64}")) throw invalid();
        String bodyHash = null;
        if (message.ci() != null) {
            validateCi(message.ci());
            try { bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
            var previous = repository.lockCiReceipt(message.runKey());
            if (previous.isPresent()) {
                if (!previous.get().equals(bodyHash)) throw new ApiException(HttpStatus.CONFLICT,
                        "PERMISSION_CALLBACK_REPLAY_CHANGED", "Callback đã lưu có nội dung khác; không ghi đè lịch sử.");
                return;
            }
            repository.createCompletedCiJob(message.runKey(), message.fingerprint());
        }
        Long actor = message.ci() != null ? null : repository.lockPendingActor(message.runKey(), message.fingerprint())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PERMISSION_CALLBACK_NOT_PENDING",
                        "Lần chạy không còn chờ nhận kết quả hoặc dấu vân tay không khớp."));
        Long reportId = null;
        if (message.report() != null) {
            ImportReport report = message.report();
            if (!validator.validate(report).isEmpty() || !message.runKey().equals(report.runKey())
                    || !message.fingerprint().equals(report.fingerprint()) || report.schemaVersion() != 2
                    || !"REGRESSION".equals(report.kind()) || !"ISOLATED_POSTGRES".equals(report.environment()))
                throw invalid();
            reportId = reviews.importAutomatedReport(report, actor).summary().id();
        }
        boolean succeeded = message.passed() && reportId != null
                && message.report().checks().stream().allMatch(c -> Boolean.TRUE.equals(c.assertionsPassed())
                    && c.expectedStatus() == c.actualStatus()
                    && (!java.util.Set.of(401,403,404,405).contains(c.expectedStatus()) || Boolean.TRUE.equals(c.resourceUnchanged())))
                && (message.ci() == null || (message.ci().total() > 0 && message.ci().failed() == 0 && message.ci().skipped() == 0
                    && message.ci().total() == message.report().checks().size()));
        repository.finishJob(message.runKey(), succeeded ? "SUCCEEDED" : "FAILED",
                succeeded ? "GitHub Actions đã hoàn thành. Mở biên bản để xem từng ca kiểm thử."
                        : "GitHub Actions chưa đạt hoặc không xuất được biên bản. Mở workflow trong GitHub Actions để xem chi tiết.",
                reportId);
        if (message.ci() != null) repository.saveCiReceipt(message.runKey(), message.ci(), bodyHash);
    }

    private void validateCi(CiRun ci) {
        if (!gateway.acceptsRepository(ci.repository()) || ci.runId() < 1 || ci.attempt() < 1
                || ci.commitSha() == null || !ci.commitSha().matches("[a-f0-9]{40,64}")
                || !"main".equals(ci.branch()) || ci.event() == null
                || !java.util.Set.of("push", "schedule", "workflow_dispatch").contains(ci.event())
                || ci.startedAt() == null || ci.completedAt() == null || ci.startedAt().isAfter(ci.completedAt())
                || ci.completedAt().isAfter(java.time.OffsetDateTime.now().plusMinutes(5))
                || ci.total() < 0 || ci.failed() < 0 || ci.skipped() < 0
                || (long)ci.failed() + ci.skipped() > ci.total()) throw invalid();
    }

    private ApiException forbidden() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REVIEW_SIGNATURE", "Chữ ký callback không hợp lệ."); }
    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_CALLBACK", "Kết quả kiểm thử không hợp lệ."); }
}
