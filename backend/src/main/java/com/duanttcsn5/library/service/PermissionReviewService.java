package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class PermissionReviewService {
    private final PermissionReviewCatalog catalog;
    private final PermissionReviewInspector inspector;
    private final PermissionReviewRepository repository;
    private final UserRepository users;

    public PermissionReviewService(PermissionReviewCatalog catalog, PermissionReviewInspector inspector,
            PermissionReviewRepository repository, UserRepository users) {
        this.catalog = catalog;
        this.inspector = inspector;
        this.repository = repository;
        this.users = users;
    }

    public Inspection inspect() { return inspector.inspect(); }

    @Transactional(readOnly = true)
    public ReportPage reports(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw invalid("Trang phải từ 0; số bản ghi mỗi trang từ 1 đến 50.");
        return repository.list(page, size);
    }

    @Transactional(readOnly = true)
    public ReportDetail detail(Long id) {
        if (id == null || id < 1) throw invalid("Mã biên bản không hợp lệ.");
        return repository.find(id, inspector.inspect().fingerprint()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PERMISSION_REPORT_NOT_FOUND", "Không tìm thấy biên bản rà soát."));
    }

    @Transactional
    public ReportDetail importReport(ImportReport report, Long actorId) {
        return saveReport(report, actorId);
    }

    /** Called only by the authenticated, signed GitHub Actions callback service. */
    @PreAuthorize("permitAll()")
    @Transactional
    public ReportDetail importAutomatedReport(ImportReport report, Long actorId) {
        return saveReport(report, actorId);
    }

    private ReportDetail saveReport(ImportReport report, Long actorId) {
        var actor = actorId == null ? null : users.findById(actorId).orElse(null);
        if (actor == null || !"ACTIVE".equals(actor.getStatus()) || actor.getRole() == null
                || !"ADMIN".equals(actor.getRole().getCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ Quản trị hệ thống được lưu biên bản rà soát.");
        }
        String fingerprint = inspector.inspect().fingerprint();
        if (!catalog.policy().version().equals(report.policyVersion())) throw invalid("Biên bản sử dụng phiên bản bảng quyền khác.");
        if (!fingerprint.equals(report.fingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "PERMISSION_BUILD_CHANGED",
                    "Source hoặc bảng quyền đã thay đổi. Chạy lại bộ kiểm thử trên đúng bản đang chạy rồi nhập kết quả.");
        }
        OffsetDateTime latest = OffsetDateTime.now().plusMinutes(5);
        if (report.startedAt().isAfter(report.completedAt()) || report.completedAt().isAfter(latest)) {
            throw invalid("Thời gian bắt đầu/kết thúc kiểm thử không hợp lệ.");
        }
        Set<String> keys = new HashSet<>();
        List<CaseResult> results = new ArrayList<>();
        for (EvidenceCase test : report.checks()) {
            PolicyEntry entry = catalog.find(test.endpointId()).orElseThrow(() -> invalid("API không thuộc phạm vi: " + test.endpointId()));
            if (!keys.add(test.endpointId() + ":" + test.httpMethod() + ":" + test.role() + ":" + test.scenario())) throw invalid("Biên bản chứa trường hợp kiểm thử trùng lặp.");
            if (report.schemaVersion() == 2 && (test.httpMethod() == null || !entry.httpMethods().contains(test.httpMethod()))) {
                throw invalid("Biên bản toàn hệ thống thiếu hoặc sai phương thức HTTP: " + test.endpointId());
            }
            int required;
            try { required = catalog.requiredStatus(entry, test.role(), test.scenario(), test.expectedStatus()); }
            catch (IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
            if (test.expectedStatus() != required) throw invalid("Kết quả mong đợi không khớp bảng quyền: " + test.endpointId() + " / " + test.role());
            if ((report.schemaVersion() == 1 && test.detectedAt().isBefore(report.startedAt())) || test.testedAt().isBefore(report.startedAt()) || test.testedAt().isAfter(report.completedAt())
                    || test.detectedAt().isAfter(test.testedAt())) throw invalid("Thời điểm phát hiện/kiểm thử lại không nằm trong lần chạy.");
            boolean denied = required == 401 || required == 403 || required == 404 || required == 405;
            boolean passed = (report.schemaVersion() == 1 || Boolean.TRUE.equals(test.assertionsPassed()))
                    && test.actualStatus() == required
                    && (!denied || Boolean.TRUE.equals(test.resourceUnchanged()));
            boolean foundBefore = test.beforeStatus() != null && test.beforeStatus() != required;
            if (foundBefore && passed && (test.correction() == null || test.correction().isBlank())) {
                throw invalid("Sai lệch đã khắc phục phải có nội dung sửa và bằng chứng kiểm thử lại.");
            }
            String outcome = !passed ? "OPEN" : foundBefore ? "FIXED" : "PASS";
            results.add(new CaseResult(test, entry.module(), entry.label(),
                    "ANONYMOUS".equals(test.role()) ? (entry.rules().containsValue("PUBLIC") ? "PUBLIC" : "LOGIN_REQUIRED") : entry.rules().get(test.role()), outcome, passed));
        }
        Long id;
        try { id = repository.insert(report, results, actorId, actor.getFullName()); }
        catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "PERMISSION_REPORT_DUPLICATE", "Lần kiểm thử này đã có biên bản; không lưu trùng.");
        }
        return repository.find(id, fingerprint).orElseThrow(() -> new IllegalStateException("Không đọc được biên bản vừa lưu."));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT", message);
    }
}
