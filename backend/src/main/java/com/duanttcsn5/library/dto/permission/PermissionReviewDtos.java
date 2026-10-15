package com.duanttcsn5.library.dto.permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PermissionReviewDtos {
    private PermissionReviewDtos() {}

    public record RoleOption(String code, String name) {}
    public record ModuleOption(String code, String name, int priority) {}
    public record PolicyEntry(String id, String module, String label, String controller,
            String handler, String path, List<String> httpMethods, Map<String, String> rules,
            String condition, boolean mutating) {}
    public record Policy(String version, String decision, List<RoleOption> roles,
            List<ModuleOption> modules, List<PolicyEntry> entries) {}
    public record RoleComparison(String role, String documented, String actualGate, boolean matches) {}
    public record ReviewedEntry(PolicyEntry policy, String guard, List<RoleComparison> roles,
            boolean mapped, boolean different) {}
    public record Inspection(String policyVersion, String decision, String fingerprint,
            OffsetDateTime inspectedAt, List<RoleOption> roles, List<ModuleOption> modules,
            int reviewedFunctions, int differentFunctions, int unresolvedFunctions,
            List<ReviewedEntry> entries) {}

    /** A test report is imported evidence, never an assertion that a live server ran these tests. */
    public record EvidenceCase(
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 150, message = "Thông tin biên bản vượt quá độ dài cho phép.") String endpointId,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "ADMIN|LIBRARY_MANAGER|LIBRARIAN|READER|ANONYMOUS", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String role,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "STANDARD|OVERRIDE|OTHER_OWNER", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String scenario,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int expectedStatus,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") Integer beforeStatus,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int actualStatus,
            Boolean resourceUnchanged,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime detectedAt,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime testedAt,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 2000, message = "Thông tin biên bản vượt quá độ dài cho phép.") String evidence,
            @Size(max = 2000, message = "Thông tin biên bản vượt quá độ dài cho phép.") String correction) {}

    public record ImportReport(
            @Min(value = 1, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 1, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int schemaVersion,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") UUID runKey,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 50, message = "Thông tin biên bản vượt quá độ dài cho phép.") String policyVersion,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "[a-f0-9]{64}", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String fingerprint,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "REGRESSION|SIMULATION", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String kind,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "MOCKMVC|ISOLATED_POSTGRES", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String environment,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 200, message = "Thông tin biên bản vượt quá độ dài cho phép.") String sourceReference,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 200, message = "Thông tin biên bản vượt quá độ dài cho phép.") String executedBy,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime startedAt,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime completedAt,
            @NotEmpty(message = "Biên bản phải có kết quả kiểm thử.") @Size(max = 2000, message = "Thông tin biên bản vượt quá độ dài cho phép.") List<@NotNull(message = "Biên bản thiếu thông tin bắt buộc.") @Valid EvidenceCase> checks) {}

    public record CaseResult(EvidenceCase test, String module, String functionName,
            String documented, String outcome, boolean passed) {}
    public record ReportSummary(Long id, UUID runKey, String kind, String environment,
            String sourceReference, String executedBy, String importedBy, OffsetDateTime importedAt,
            OffsetDateTime completedAt, int caseCount, int detectedCount, int fixedCount, int failedCount) {}
    public record ReportDetail(ReportSummary summary, ImportReport evidence,
            List<CaseResult> results, boolean currentFingerprint) {}
    public record ReportPage(List<ReportSummary> items, int page, int size, long totalItems) {}
}
