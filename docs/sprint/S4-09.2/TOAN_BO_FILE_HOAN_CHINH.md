# Toàn bộ file code hoàn chỉnh — S4-09.2

Các file trong ZIP là bản dùng trực tiếp. Tài liệu này chép đầy đủ cùng nội dung để đối chiếu; không phải snippet. Không dán toàn bộ tài liệu này vào một file source. Giữ các file không có trong danh sách từ source gốc.

## backend/src/main/java/com/duanttcsn5/library/controller/AuthorController.java

````java
package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.author.AuthorResponse;
import com.duanttcsn5.library.dto.author.CreateAuthorRequest;
import com.duanttcsn5.library.dto.author.UpdateAuthorRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.AuthorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AuthorResponse>> getAllAuthors() {
        return ResponseEntity.ok(authorService.getAllAuthors());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AuthorResponse>> getActiveAuthors() {
        return ResponseEntity.ok(authorService.getActiveAuthors());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuthorResponse> getAuthorById(@PathVariable Long id) {
        return ResponseEntity.ok(authorService.getAuthorById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<AuthorResponse> createAuthor(
            @Valid @RequestBody CreateAuthorRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.createAuthor(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<AuthorResponse> updateAuthor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAuthorRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.updateAuthor(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<AuthorResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<Void> deleteAuthor(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        authorService.deleteAuthor(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/controller/CardTypeController.java

````java
package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.cardtype.CardTypeResponse;
import com.duanttcsn5.library.dto.cardtype.CreateCardTypeRequest;
import com.duanttcsn5.library.dto.cardtype.PolicyHistoryResponse;
import com.duanttcsn5.library.dto.cardtype.UpdateCardTypeRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.CardTypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/card-types")
public class CardTypeController {

    private final CardTypeService cardTypeService;

    public CardTypeController(CardTypeService cardTypeService) {
        this.cardTypeService = cardTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<CardTypeResponse>> getAllCardTypes() {
        return ResponseEntity.ok(cardTypeService.getAllCardTypes());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<CardTypeResponse>> getActiveCardTypes() {
        return ResponseEntity.ok(cardTypeService.getActiveCardTypes());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> getCardTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(cardTypeService.getCardTypeById(id));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<PolicyHistoryResponse>> getPolicyHistory() {
        return ResponseEntity.ok(cardTypeService.getPolicyHistory());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> createCardType(
            @Valid @RequestBody CreateCardTypeRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.createCardType(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> updateCardType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCardTypeRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.updateCardType(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteCardType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        cardTypeService.deleteCardType(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/controller/CategoryController.java

````java
package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.category.CategoryResponse;
import com.duanttcsn5.library.dto.category.CreateCategoryRequest;
import com.duanttcsn5.library.dto.category.UpdateCategoryRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.createCategory(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.updateCategory(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<CategoryResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        categoryService.deleteCategory(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/controller/PermissionReviewController.java

````java
package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.PermissionReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/permission-review")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionReviewController {
    private final PermissionReviewService service;
    @org.springframework.beans.factory.annotation.Autowired private com.duanttcsn5.library.service.PermissionReviewRunService runs;
    @org.springframework.beans.factory.annotation.Autowired private com.duanttcsn5.library.service.PermissionReviewCoverageService coverage;
    public PermissionReviewController(PermissionReviewService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Inspection> inspect() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.inspect());
    }
    @GetMapping("/reports")
    public ResponseEntity<ReportPage> reports(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.reports(page, size));
    }
    @GetMapping("/reports/{id}")
    public ResponseEntity<ReportDetail> detail(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.detail(id));
    }
    @PostMapping("/reports")
    public ResponseEntity<ReportDetail> importReport(@Valid @RequestBody ImportReport report,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importReport(report, principal.id()));
    }

    @PostMapping("/runs")
    public ResponseEntity<RunState> startRun(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runs.start(principal.id()));
    }
    @GetMapping("/runs/latest")
    public ResponseEntity<RunState> latestRun() {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(runs.latest());}
    @GetMapping("/runs/{runKey}")
    public ResponseEntity<RunState> runStatus(@PathVariable java.util.UUID runKey) {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(runs.get(runKey));}
    @GetMapping("/reports/{id}/coverage")
    public ResponseEntity<Coverage> coverage(@PathVariable Long id) {return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(coverage.coverage(id));}
    @PostMapping("/reports/{id}/complete")
    public ResponseEntity<Coverage> complete(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal) {return ResponseEntity.ok(coverage.complete(id,principal.id()));}
    @GetMapping("/reports/{id}/export")
    public ResponseEntity<ExportReport> export(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Content-Disposition","attachment; filename=BienBan_S4-09.2_"+id+".json").body(coverage.export(id));
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/dto/permission/PermissionReviewDtos.java

````java
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
            List<ReviewedEntry> entries, int totalApis, List<String> undocumentedApis) {
        public Inspection(String policyVersion, String decision, String fingerprint, OffsetDateTime inspectedAt,
                List<RoleOption> roles, List<ModuleOption> modules, int reviewedFunctions, int differentFunctions,
                int unresolvedFunctions, List<ReviewedEntry> entries) {
            this(policyVersion, decision, fingerprint, inspectedAt, roles, modules, reviewedFunctions,
                    differentFunctions, unresolvedFunctions, entries,
                    entries.stream().mapToInt(e -> e.policy().httpMethods().size()).sum(), List.of());
        }
    }

    /** A test report is imported evidence, never an assertion that a live server ran these tests. */
    public record EvidenceCase(
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 150, message = "Thông tin biên bản vượt quá độ dài cho phép.") String endpointId,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "ADMIN|LIBRARY_MANAGER|LIBRARIAN|READER|ANONYMOUS", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String role,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "STANDARD|OVERRIDE|OTHER_OWNER|SPOOFED_OWNER", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String scenario,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int expectedStatus,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") Integer beforeStatus,
            @Min(value = 100, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 599, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int actualStatus,
            Boolean resourceUnchanged,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime detectedAt,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime testedAt,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 2000, message = "Thông tin biên bản vượt quá độ dài cho phép.") String evidence,
            @Size(max = 2000, message = "Thông tin biên bản vượt quá độ dài cho phép.") String correction,
            @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "Phương thức HTTP không hợp lệ.") String httpMethod, Boolean assertionsPassed) {
        public EvidenceCase(String endpointId, String role, String scenario, int expectedStatus,
                Integer beforeStatus, int actualStatus, Boolean resourceUnchanged, OffsetDateTime detectedAt,
                OffsetDateTime testedAt, String evidence, String correction) {
            this(endpointId, role, scenario, expectedStatus, beforeStatus, actualStatus, resourceUnchanged,
                    detectedAt, testedAt, evidence, correction, null, null);
        }
    }

    public record ImportReport(
            @Min(value = 1, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") @Max(value = 2, message = "Mã HTTP hoặc phiên bản biên bản không hợp lệ.") int schemaVersion,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") UUID runKey,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 50, message = "Thông tin biên bản vượt quá độ dài cho phép.") String policyVersion,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "[a-f0-9]{64}", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String fingerprint,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "REGRESSION|SIMULATION", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String kind,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Pattern(regexp = "MOCKMVC|ISOLATED_POSTGRES", message = "Vai trò, môi trường hoặc dấu vân tay biên bản không hợp lệ.") String environment,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 200, message = "Thông tin biên bản vượt quá độ dài cho phép.") String sourceReference,
            @NotBlank(message = "Thông tin biên bản không được để trống.") @Size(max = 200, message = "Thông tin biên bản vượt quá độ dài cho phép.") String executedBy,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime startedAt,
            @NotNull(message = "Biên bản thiếu thông tin bắt buộc.") OffsetDateTime completedAt,
            @NotEmpty(message = "Biên bản phải có kết quả kiểm thử.") @Size(max = 5000, message = "Thông tin biên bản vượt quá độ dài cho phép.") List<@NotNull(message = "Biên bản thiếu thông tin bắt buộc.") @Valid EvidenceCase> checks) {}

    public record CaseResult(EvidenceCase test, String module, String functionName,
            String documented, String outcome, boolean passed) {}
    public record ReportSummary(Long id, UUID runKey, String kind, String environment,
            String sourceReference, String executedBy, String importedBy, OffsetDateTime importedAt,
            OffsetDateTime completedAt, int caseCount, int detectedCount, int fixedCount, int failedCount) {}
    public record ReportDetail(ReportSummary summary, ImportReport evidence,
            List<CaseResult> results, boolean currentFingerprint) {}
    public record ReportPage(List<ReportSummary> items, int page, int size, long totalItems) {}
    public record MissingRoleCase(String endpointId, String httpMethod, String path, String role, String scenario) {}
    public record Completion(Long reportId, String confirmedBy, OffsetDateTime confirmedAt, String fingerprint) {}
    public record Coverage(int totalApis, int coveredApis, int requiredRoleCases, int coveredRoleCases,
            int passedCases, int failedCases, int unresolvedFindings, int matrixDifferences,
            int unknownFunctions, boolean currentSource, boolean serverExecuted, boolean eligible,
            List<MissingRoleCase> missing, List<String> reasons, Completion completion) {}
    public record RunState(UUID runKey, String state, String message, String requestedBy,
            OffsetDateTime startedAt, OffsetDateTime finishedAt, Long reportId) {}
    public record ExportReport(ReportDetail report, Coverage coverage, List<ReportDetail> sprintReports) {}
}
````

## backend/src/main/java/com/duanttcsn5/library/repository/BookIncidentRepository.java

````java
package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.incident.IncidentDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BookIncidentRepository {
    private final JdbcTemplate jdbc;
    public BookIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Factors factors(boolean lock) {
        return jdbc.queryForObject("SELECT * FROM library_compensation_settings WHERE id = 1" + (lock ? " FOR SHARE" : ""),
                (rs, n) -> new Factors(rs.getBigDecimal("lost_factor"), rs.getBigDecimal("damaged_factor"),
                        rs.getObject("updated_at", OffsetDateTime.class)));
    }
    public void updateFactors(Factors factors, Long actor) {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=?, damaged_factor=?, updated_by=?, updated_at=clock_timestamp() WHERE id=1",
                factors.lost(), factors.damaged(), actor);
    }
    private static final String ITEM_SELECT = """
            SELECT li.id AS item_id, l.id AS loan_id, l.loan_number, l.borrower_user_id, u.full_name,
                   c.id AS copy_id, c.barcode, b.title, li.due_date, c.status, li.returned_at,
                   li.resolution_reason, c.cover_price
            FROM loan_items li JOIN loans l ON l.id=li.loan_id JOIN users u ON u.id=l.borrower_user_id
            JOIN book_copies c ON c.id=li.book_copy_id JOIN books b ON b.id=c.book_id
            """;
    private static final RowMapper<Item> ITEM_MAPPER = (rs, n) -> new Item(rs.getLong("item_id"), rs.getLong("loan_id"),
            rs.getString("loan_number"), rs.getLong("borrower_user_id"), rs.getString("full_name"),
            rs.getLong("copy_id"), rs.getString("barcode"), rs.getString("title"),
            rs.getObject("due_date", OffsetDateTime.class), rs.getString("status"),
            rs.getObject("returned_at", OffsetDateTime.class), rs.getString("resolution_reason"), rs.getBigDecimal("cover_price"));
    public List<Item> search(String query) {
        return jdbc.query(ITEM_SELECT + """
                WHERE li.returned_at IS NULL AND (strpos(lower(l.loan_number),lower(?))>0
                  OR strpos(lower(c.barcode),lower(?))>0 OR strpos(lower(u.full_name),lower(?))>0
                  OR EXISTS (SELECT 1 FROM library_cards card WHERE card.user_id=u.id
                      AND strpos(lower(card.card_number),lower(?))>0))
                ORDER BY l.borrowed_at DESC,l.id DESC,li.id LIMIT 100
                """, ITEM_MAPPER, query,query,query,query);
    }
    public Optional<Item> item(Long id) {
        return jdbc.query(ITEM_SELECT + " WHERE li.id=?", ITEM_MAPPER, id).stream().findFirst();
    }
    public void lockReader(Long readerId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE", Long.class, readerId);
    }
    public Long compensation(Item item, String type, long amount) {
        // Zero rounded VND still has a receivable/audit record; payment lists omit zero debt.
        return jdbc.queryForObject("""
                INSERT INTO reader_fees(reader_user_id,description,amount_vnd,compensation_item_id)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class,item.readerId(),"Bồi thường " + ("LOST".equals(type)?"Mất":"Hỏng nặng")
                + " · " + item.loanNumber() + " · " + item.barcode(),amount,item.itemId());
    }
    public Long insert(Item item,String type,String description,BigDecimal factor,long amount,Long feeId,
                       Long actor,String actorName,OffsetDateTime at,long automatic,String adjustmentReason) {
        return jdbc.queryForObject("""
                INSERT INTO book_incidents(item_id,copy_id,reader_user_id,incident_type,description,cover_price,
                  factor,compensation_vnd,compensation_fee_id,actor_user_id,actor_name,recorded_at,before_status,after_status,automatic_compensation_vnd,adjustment_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'BORROWED','REMOVED',?,?) RETURNING id
                """,Long.class,item.itemId(),item.copyId(),item.readerId(),type,description,item.coverPrice(),
                factor,amount,feeId,actor,actorName,at,automatic,adjustmentReason);
    }
    public void adjustmentAudit(Long id,Item item,BigDecimal factor,long before,long after,String reason,Long actor,String actorName) {
        // PostgreSQL constructs JSON safely; the audit participates in the incident transaction.
        jdbc.update("""
                INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,after_data,created_at)
                VALUES (?,'COMPENSATION_ADJUSTED','BOOK_INCIDENT',?,
                  jsonb_build_object('itemId',CAST(? AS bigint),'coverPrice',CAST(? AS numeric),
                    'factor',CAST(? AS numeric),'beforeAmountVnd',CAST(? AS bigint),'afterAmountVnd',CAST(? AS bigint),
                    'reason',CAST(? AS text),'actorUserId',CAST(? AS bigint),'actorName',CAST(? AS text)),CURRENT_TIMESTAMP)
                """,actor,id.toString(),item.itemId(),item.coverPrice(),factor,before,after,reason,actor,actorName);
    }
    public int close(Item item,String type,OffsetDateTime at,Long actor,String actorName) {
        return jdbc.update("""
                UPDATE loan_items SET returned_at=?,returned_by=?,returned_by_name=?,resolution_reason=?
                WHERE id=? AND returned_at IS NULL
                """,at,actor,actorName,type,item.itemId());
    }
    public void history(Item item,String type,String description,Long actor,String actorName,OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,changed_at,reason)
                VALUES (?,'BORROWED','REMOVED',?,?,?,?)
                """,item.copyId(),actor,actorName,at,("LOST".equals(type)?"Mất: ":"Hỏng nặng: ")+description);
    }
    public void photo(Long id,String filename,String contentType,byte[] bytes) {
        jdbc.update("INSERT INTO book_incident_images(incident_id,filename,content_type,image_data) VALUES (?,?,?,?)",
                id,filename,contentType,bytes);
    }
    public List<Photo> photos(Long id) {
        return jdbc.query("SELECT id,filename,content_type FROM book_incident_images WHERE incident_id=? ORDER BY id",
                (rs,n)->new Photo(rs.getLong("id"),rs.getString("filename"),rs.getString("content_type")),id);
    }
    public Optional<Image> image(Long incidentId,Long imageId) {
        return jdbc.query("SELECT content_type,image_data FROM book_incident_images WHERE incident_id=? AND id=?",
                (rs,n)->new Image(rs.getString("content_type"),rs.getBytes("image_data")),incidentId,imageId).stream().findFirst();
    }
    public record Row(Long id,Long itemId,String type,String description,BigDecimal price,BigDecimal factor,
                      long amount,Long feeId,String actor,OffsetDateTime at,String before,String after,long automatic,String reason,Long actorId) {}
    public Optional<Row> row(Long id) {
        return jdbc.query("SELECT * FROM book_incidents WHERE id=?",(rs,n)->new Row(rs.getLong("id"),rs.getLong("item_id"),
                rs.getString("incident_type"),rs.getString("description"),rs.getBigDecimal("cover_price"),rs.getBigDecimal("factor"),
                rs.getLong("compensation_vnd"),rs.getLong("compensation_fee_id"),rs.getString("actor_name"),
                rs.getObject("recorded_at",OffsetDateTime.class),rs.getString("before_status"),rs.getString("after_status"),rs.getLong("automatic_compensation_vnd"),rs.getString("adjustment_reason"),rs.getLong("actor_user_id")),id).stream().findFirst();
    }
    public List<Long> incidentIds(String query) {
        return jdbc.query("""
                SELECT i.id FROM book_incidents i JOIN loan_items li ON li.id=i.item_id
                JOIN loans l ON l.id=li.loan_id JOIN book_copies c ON c.id=i.copy_id JOIN users u ON u.id=i.reader_user_id
                WHERE strpos(lower(l.loan_number),lower(?))>0 OR strpos(lower(c.barcode),lower(?))>0
                  OR strpos(lower(u.full_name),lower(?))>0 ORDER BY i.recorded_at DESC,i.id DESC LIMIT 100
                """,(rs,n)->rs.getLong("id"),query,query,query);
    }
    public List<Adjustment> adjustments(Long id) {
        return jdbc.query("SELECT * FROM book_incident_adjustments WHERE incident_id=? ORDER BY id",(rs,n)->
            new Adjustment(rs.getLong("id"),rs.getString("adjustment_number"),rs.getLong("incident_id"),
            rs.getString("reason"),rs.getString("case_type"),rs.getString("target_status"),rs.getString("status"),
            rs.getString("requester_name"),rs.getObject("requested_at",OffsetDateTime.class),rs.getString("confirmer_name"),
            rs.getObject("confirmed_at",OffsetDateTime.class),rs.getObject("reversed_vnd",Long.class),
            rs.getObject("cancelled_debt_vnd",Long.class),rs.getObject("refund_due_vnd",Long.class),
            rs.getString("before_data"),rs.getString("after_data")),id);
    }
    public Long bookId(Long copyId) { return jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,copyId); }
    public OffsetDateTime now() { return jdbc.queryForObject("SELECT clock_timestamp()",OffsetDateTime.class); }
    public void requestedAdjustmentAudit(Long id,Long actor) {
        jdbc.update("""
            INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,after_data,created_at)
            SELECT ?,'INCIDENT_ADJUSTMENT_REQUESTED','BOOK_INCIDENT_ADJUSTMENT',id::text,to_jsonb(a),clock_timestamp()
            FROM book_incident_adjustments a WHERE id=?
            """,actor,id);
    }
    public void lockIncident(Long id) { jdbc.queryForObject("SELECT id FROM book_incidents WHERE id=? FOR UPDATE",Long.class,id); }
    public long paid(Long feeId,boolean lock) {
        return jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?"+(lock?" FOR UPDATE":""),Long.class,feeId);
    }
    public Long requestAdjustment(Long incidentId,AdjustmentRequest request,Long actor,String name) {
        return jdbc.queryForObject("""
            INSERT INTO book_incident_adjustments(incident_id,reason,case_type,target_status,requested_by,requester_name)
            VALUES (?,?,?,?,?,?) RETURNING id
            """,Long.class,incidentId,request.reason(),request.caseType(),request.targetStatus(),actor,name);
    }
    public String snapshot(Item item) {
        return jdbc.queryForObject("""
            SELECT jsonb_build_object('item',to_jsonb(li),'copy',to_jsonb(c),'fee',to_jsonb(f),
              'loanStatus',CAST(? AS text))::text FROM loan_items li JOIN book_copies c ON c.id=li.book_copy_id
              JOIN reader_fees f ON f.compensation_item_id=li.id WHERE li.id=?
            """,String.class,loanStatus(item.loanId()),item.itemId());
    }
    public OffsetDateTime confirmAdjustment(Long id,Long actor,String name,String before,String after,OffsetDateTime at) {
        return jdbc.queryForObject("""
            UPDATE book_incident_adjustments SET status='CONFIRMED',confirmed_by=?,confirmer_name=?,
              before_data=CAST(? AS jsonb),after_data=CAST(? AS jsonb),confirmed_at=? WHERE id=? AND status='PENDING' RETURNING confirmed_at
            """,OffsetDateTime.class,actor,name,before,after,at,id);
    }
    public String projectedSnapshot(Item item,String target,String copyStatus,OffsetDateTime at,Long actor,String name) {
        boolean reopen="BORROWED".equals(target);
        return jdbc.queryForObject("""
            SELECT jsonb_build_object('item',to_jsonb(li)||jsonb_build_object('returned_at',CAST(? AS timestamptz),
              'returned_by',CAST(? AS bigint),'returned_by_name',CAST(? AS text),'resolution_reason',CAST(? AS text)),
              'copy',to_jsonb(c)||jsonb_build_object('status',CAST(? AS text),'updated_at',CURRENT_TIMESTAMP), 'fee',to_jsonb(f),
              'loanStatus',CAST(? AS text))::text
            FROM loan_items li JOIN book_copies c ON c.id=li.book_copy_id
              JOIN reader_fees f ON f.compensation_item_id=li.id WHERE li.id=?
            """,String.class,reopen?null:at,reopen?null:actor,reopen?null:name,reopen?null:"RETURNED",copyStatus,
            projectedLoanStatus(item,target),item.itemId());
    }
    public void applyAdjustment(Item item,Adjustment a,OffsetDateTime at,Long actor,String name,String copyStatus) {
        boolean reopen="BORROWED".equals(a.targetStatus());
        jdbc.update("UPDATE loan_items SET returned_at=?,returned_by=?,returned_by_name=?,resolution_reason=? WHERE id=?",
            reopen?null:at,reopen?null:actor,reopen?null:name,reopen?null:"RETURNED",item.itemId());
        jdbc.update("UPDATE book_copies SET status=? WHERE id=?",copyStatus,item.copyId());
        jdbc.update("""
            INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,changed_at,reason)
            VALUES (?,'REMOVED',?,?,?,?,?)
            """,item.copyId(),copyStatus,actor,name,at,a.number()+": "+a.reason());
    }
    public void adjustmentAudit(Adjustment a,Long actor,String before,String after) {
        jdbc.update("""
            INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,before_data,after_data,created_at)
            VALUES (?,'INCIDENT_REVERSED','BOOK_INCIDENT_ADJUSTMENT',?,CAST(? AS jsonb),
              jsonb_build_object('adjustment',CAST(? AS jsonb),'values',CAST(? AS jsonb)),clock_timestamp())
            """,actor,a.id().toString(),before,jdbc.queryForObject("SELECT to_jsonb(a)::text FROM book_incident_adjustments a WHERE id=?",String.class,a.id()),after);
    }
    public String projectedLoanStatus(Item item,String target) {
        if ("BORROWED".equals(target)) return "BORROWED";
        return jdbc.queryForObject("""
            SELECT CASE WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND returned_at IS NULL) THEN 'BORROWED'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='LOST')
             AND EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='DAMAGED') THEN 'MIXED_INCIDENT'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='LOST') THEN 'LOST'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='DAMAGED') THEN 'DAMAGED'
            ELSE 'RETURNED' END
            """,String.class,item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId());
    }
    public String loanStatus(Long loanId) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND returned_at IS NULL) THEN 'BORROWED'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='LOST')
                    AND EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='DAMAGED') THEN 'MIXED_INCIDENT'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='LOST') THEN 'LOST'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='DAMAGED') THEN 'DAMAGED'
                  ELSE 'RETURNED' END
                """,String.class,loanId,loanId,loanId,loanId,loanId);
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/repository/LoanReminderRepository.java

````java
package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderRetryAttemptDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LoanReminderRepository {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public LoanReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EligibleLoanItemRow(
            Long itemId,
            Long loanId,
            String loanNumber,
            Long readerId,
            String readerName,
            String readerCardNumber,
            String readerEmail,
            Long bookId,
            String bookTitle,
            String copyBarcode,
            OffsetDateTime dueDate
    ) {
        public EligibleLoanItemRow(
                Long itemId, Long loanId, String loanNumber, Long readerId,
                String readerName, String readerEmail, Long bookId, String bookTitle,
                String copyBarcode, OffsetDateTime dueDate
        ) {
            this(itemId, loanId, loanNumber, readerId, readerName, null, readerEmail, bookId, bookTitle, copyBarcode, dueDate);
        }
    }

    /**
     * Find active, unreturned loan items due on:
     * - targetDate + 2 days (DUE_IN_2_DAYS)
     * - targetDate (DUE_TODAY)
     * - targetDate - 1 day (OVERDUE_1_DAY)
     * - targetDate - 7 days (OVERDUE_7_DAYS)
     * - targetDate - 30 days (OVERDUE_30_DAYS)
     * evaluated in Vietnam time. Excludes non-qualifying days, returned and closed items.
     */
    public List<EligibleLoanItemRow> findEligibleItemsForDate(LocalDate targetDate) {
        LocalDate in2Days = targetDate.plusDays(2);
        LocalDate overdue1 = targetDate.minusDays(1);
        LocalDate overdue7 = targetDate.minusDays(7);
        LocalDate overdue30 = targetDate.minusDays(30);

        String sql = """
            SELECT li.id AS item_id,
                   li.loan_id,
                   l.loan_number,
                   l.borrower_user_id AS reader_id,
                   u.full_name AS reader_name,
                   lc.card_number AS reader_card_number,
                   u.email AS reader_email,
                   b.id AS book_id,
                   b.title AS book_title,
                   c.barcode AS copy_barcode,
                   li.due_date
            FROM loan_items li
            JOIN loans l ON l.id = li.loan_id
            JOIN users u ON u.id = l.borrower_user_id
            LEFT JOIN library_cards lc ON lc.user_id = u.id
            JOIN book_copies c ON c.id = li.book_copy_id
            JOIN books b ON b.id = c.book_id
            WHERE li.returned_at IS NULL
              AND (li.resolution_reason IS NULL OR li.resolution_reason NOT IN ('RETURNED', 'LOST', 'DAMAGED'))
              AND (c.status = 'BORROWED')
              AND (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date IN (?, ?, ?, ?, ?)
            ORDER BY l.borrower_user_id ASC, li.due_date ASC, li.id ASC
            """;

        return jdbc.query(sql, (rs, rowNum) -> new EligibleLoanItemRow(
                rs.getLong("item_id"),
                rs.getLong("loan_id"),
                rs.getString("loan_number"),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                rs.getString("reader_card_number"),
                rs.getString("reader_email"),
                rs.getLong("book_id"),
                rs.getString("book_title"),
                rs.getString("copy_barcode"),
                rs.getObject("due_date", OffsetDateTime.class)
        ), Date.valueOf(in2Days), Date.valueOf(targetDate),
           Date.valueOf(overdue1), Date.valueOf(overdue7), Date.valueOf(overdue30));
    }

    public boolean isAlreadySentToday(LocalDate reminderDate, Long readerId, String reminderGroup) {
        String sql = """
            SELECT COUNT(*) FROM loan_reminder_logs
            WHERE reminder_date = ?
              AND reader_id = ?
              AND reminder_group = ?
              AND status IN ('SENT', 'PENDING_RETRY', 'FAILED_MAX_RETRIES')
              AND is_dry_run = FALSE
            """;
        Integer count = jdbc.queryForObject(sql, Integer.class, Date.valueOf(reminderDate), readerId, reminderGroup);
        return count != null && count > 0;
    }

    public Long insertLog(
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String readerCardNumber,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            long totalEstimatedFeeVnd,
            int retryCount,
            int maxRetries,
            String loanNumbers,
            String bookTitles,
            String itemsDetailJson,
            String subject,
            String emailContent,
            String status,
            String errorMessage,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName,
            boolean isDryRun
    ) {
        String sql = """
            INSERT INTO loan_reminder_logs (
                reminder_date, reader_id, reader_name, reader_card_number, recipient_email,
                reminder_group, loan_item_count, total_estimated_fee_vnd, retry_count, max_retries,
                loan_numbers, book_titles, items_detail, subject, email_content, status, error_message,
                triggered_by, triggered_by_user_id, triggered_by_user_name, is_dry_run, sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setDate(1, Date.valueOf(reminderDate));
            ps.setLong(2, readerId);
            ps.setString(3, readerName);
            ps.setString(4, readerCardNumber);
            ps.setString(5, recipientEmail);
            ps.setString(6, reminderGroup);
            ps.setInt(7, loanItemCount);
            ps.setLong(8, totalEstimatedFeeVnd);
            ps.setInt(9, retryCount);
            ps.setInt(10, maxRetries);
            ps.setString(11, loanNumbers);
            ps.setString(12, bookTitles);
            ps.setString(13, itemsDetailJson != null ? itemsDetailJson : "[]");
            ps.setString(14, subject);
            ps.setString(15, emailContent);
            ps.setString(16, status);
            ps.setString(17, errorMessage);
            ps.setString(18, triggeredBy);
            if (triggeredByUserId != null) {
                ps.setLong(19, triggeredByUserId);
            } else {
                ps.setNull(19, java.sql.Types.BIGINT);
            }
            ps.setString(20, triggeredByUserName);
            ps.setBoolean(21, isDryRun);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    // Overload for backward compatibility in existing tests
    public Long insertLog(
            LocalDate reminderDate, Long readerId, String readerName, String recipientEmail,
            String reminderGroup, int loanItemCount, long totalEstimatedFeeVnd, String loanNumbers,
            String bookTitles, String itemsDetailJson, String subject, String emailContent,
            String status, String errorMessage, String triggeredBy, Long triggeredByUserId,
            String triggeredByUserName, boolean isDryRun
    ) {
        return insertLog(
                reminderDate, readerId, readerName, null, recipientEmail,
                reminderGroup, loanItemCount, totalEstimatedFeeVnd, 0, 3,
                loanNumbers, bookTitles, itemsDetailJson, subject, emailContent,
                status, errorMessage, triggeredBy, triggeredByUserId, triggeredByUserName, isDryRun
        );
    }

    public void insertRetryAttempt(
            Long reminderLogId,
            int attemptNumber,
            String status,
            String errorMessage,
            String triggeredBy
    ) {
        String sql = """
            INSERT INTO loan_reminder_retry_attempts (
                reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbc.update(sql, reminderLogId, attemptNumber, status, errorMessage, triggeredBy);
    }

    public List<LoanReminderRetryAttemptDto> findAttemptsForLog(Long reminderLogId) {
        String sql = """
            SELECT id, reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            FROM loan_reminder_retry_attempts
            WHERE reminder_log_id = ?
            ORDER BY attempt_number ASC
            """;
        return jdbc.query(sql, (rs, rowNum) -> new LoanReminderRetryAttemptDto(
                rs.getLong("id"),
                rs.getLong("reminder_log_id"),
                rs.getInt("attempt_number"),
                rs.getObject("attempted_at", OffsetDateTime.class),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("triggered_by")
        ), reminderLogId);
    }

    public Map<Long, List<LoanReminderRetryAttemptDto>> findAttemptsForLogs(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) return Collections.emptyMap();
        String inSql = String.join(",", Collections.nCopies(logIds.size(), "?"));
        String sql = String.format("""
            SELECT id, reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            FROM loan_reminder_retry_attempts
            WHERE reminder_log_id IN (%s)
            ORDER BY reminder_log_id ASC, attempt_number ASC
            """, inSql);

        Map<Long, List<LoanReminderRetryAttemptDto>> map = new LinkedHashMap<>();
        jdbc.query(sql, (rs) -> {
            Long logId = rs.getLong("reminder_log_id");
            LoanReminderRetryAttemptDto dto = new LoanReminderRetryAttemptDto(
                    rs.getLong("id"),
                    logId,
                    rs.getInt("attempt_number"),
                    rs.getObject("attempted_at", OffsetDateTime.class),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    rs.getString("triggered_by")
            );
            map.computeIfAbsent(logId, k -> new ArrayList<>()).add(dto);
        }, logIds.toArray());
        return map;
    }

    public void updateLogRetryStatus(Long logId, String newStatus, int retryCount, String lastError) {
        String sql = """
            UPDATE loan_reminder_logs
            SET status = ?, retry_count = ?, error_message = ?, sent_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        jdbc.update(sql, newStatus, retryCount, lastError, logId);
    }

    public Optional<LoanReminderLogResponse> findLogById(Long id) {
        String sql = "SELECT * FROM loan_reminder_logs WHERE id = ?";
        List<LoanReminderLogResponse> list = jdbc.query(sql, (rs, rowNum) -> mapLogRow(rs), id);
        if (list.isEmpty()) return Optional.empty();
        LoanReminderLogResponse base = list.get(0);
        List<LoanReminderRetryAttemptDto> attempts = findAttemptsForLog(id);
        return Optional.of(new LoanReminderLogResponse(
                base.id(), base.reminderDate(), base.readerId(), base.readerName(),
                base.readerCardNumber(), base.recipientEmail(), base.reminderGroup(),
                base.loanItemCount(), base.totalEstimatedFeeVnd(), base.retryCount(),
                base.maxRetries(), base.loanNumbers(), base.bookTitles(), base.itemsDetail(),
                base.subject(), base.emailContent(), base.status(), base.errorMessage(),
                base.triggeredBy(), base.triggeredByUserName(), base.isDryRun(),
                base.sentAt(), attempts
        ));
    }

    public List<LoanReminderLogResponse> findPendingRetryLogs() {
        String sql = """
            SELECT * FROM loan_reminder_logs
            WHERE status = 'PENDING_RETRY' AND retry_count < max_retries
            ORDER BY sent_at ASC, id ASC
            """;
        return jdbc.query(sql, (rs, rowNum) -> mapLogRow(rs));
    }

    public List<LoanReminderLogResponse> findLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            String search,
            String cardNumber,
            int offset,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM loan_reminder_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND reminder_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (reminderGroup != null && !reminderGroup.isBlank()) {
            sql.append("AND reminder_group = ? ");
            params.add(reminderGroup.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND status = ? ");
            params.add(status.trim());
        }
        if (cardNumber != null && !cardNumber.isBlank()) {
            sql.append("AND reader_card_number ILIKE ? ");
            params.add("%" + cardNumber.trim() + "%");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (reader_name ILIKE ? OR recipient_email ILIKE ? OR reader_card_number ILIKE ? OR loan_numbers ILIKE ?) ");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        sql.append("ORDER BY sent_at DESC, id DESC OFFSET ? LIMIT ?");
        params.add(Math.max(0, offset));
        params.add(Math.max(1, limit));

        List<LoanReminderLogResponse> logs = jdbc.query(sql.toString(), (rs, rowNum) -> mapLogRow(rs), params.toArray());
        if (logs.isEmpty()) return logs;

        List<Long> ids = logs.stream().map(LoanReminderLogResponse::id).toList();
        Map<Long, List<LoanReminderRetryAttemptDto>> attemptsMap = findAttemptsForLogs(ids);

        return logs.stream().map(l -> new LoanReminderLogResponse(
                l.id(), l.reminderDate(), l.readerId(), l.readerName(),
                l.readerCardNumber(), l.recipientEmail(), l.reminderGroup(),
                l.loanItemCount(), l.totalEstimatedFeeVnd(), l.retryCount(),
                l.maxRetries(), l.loanNumbers(), l.bookTitles(), l.itemsDetail(),
                l.subject(), l.emailContent(), l.status(), l.errorMessage(),
                l.triggeredBy(), l.triggeredByUserName(), l.isDryRun(),
                l.sentAt(), attemptsMap.getOrDefault(l.id(), List.of())
        )).toList();
    }

    public List<LoanReminderLogResponse> findLogs(LocalDate date, String reminderGroup, String status, int offset, int limit) {
        return findLogs(date, reminderGroup, status, null, null, offset, limit);
    }

    public long countLogs(LocalDate date, String reminderGroup, String status, String search, String cardNumber) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM loan_reminder_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND reminder_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (reminderGroup != null && !reminderGroup.isBlank()) {
            sql.append("AND reminder_group = ? ");
            params.add(reminderGroup.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND status = ? ");
            params.add(status.trim());
        }
        if (cardNumber != null && !cardNumber.isBlank()) {
            sql.append("AND reader_card_number ILIKE ? ");
            params.add("%" + cardNumber.trim() + "%");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (reader_name ILIKE ? OR recipient_email ILIKE ? OR reader_card_number ILIKE ? OR loan_numbers ILIKE ?) ");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    public long countLogs(LocalDate date, String reminderGroup, String status) {
        return countLogs(date, reminderGroup, status, null, null);
    }

    private LoanReminderLogResponse mapLogRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String cardNum = null;
        try {
            cardNum = rs.getString("reader_card_number");
        } catch (Exception ignored) {}

        int retryCnt = 0;
        try {
            retryCnt = rs.getInt("retry_count");
        } catch (Exception ignored) {}

        int maxRetr = 3;
        try {
            maxRetr = rs.getInt("max_retries");
            if (maxRetr <= 0) maxRetr = 3;
        } catch (Exception ignored) {}

        return new LoanReminderLogResponse(
                rs.getLong("id"),
                rs.getDate("reminder_date").toLocalDate(),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                cardNum,
                rs.getString("recipient_email"),
                rs.getString("reminder_group"),
                rs.getInt("loan_item_count"),
                rs.getLong("total_estimated_fee_vnd"),
                retryCnt,
                maxRetr,
                rs.getString("loan_numbers"),
                rs.getString("book_titles"),
                rs.getString("items_detail"),
                rs.getString("subject"),
                rs.getString("email_content"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("triggered_by"),
                rs.getString("triggered_by_user_name"),
                rs.getBoolean("is_dry_run"),
                rs.getObject("sent_at", OffsetDateTime.class),
                List.of()
        );
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/repository/PermissionReviewRepository.java

````java
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
````

## backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCatalog.java

````java
package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Versioned PO-approved document; it does not grant runtime permissions. */
@Component
public class PermissionReviewCatalog {
    private final Policy policy;
    private final byte[] source;
    private final Map<String, PolicyEntry> entries;

    @org.springframework.beans.factory.annotation.Autowired
    public PermissionReviewCatalog(ObjectMapper mapper) throws IOException {
        this(mapper, "permissions/S4-09.2-policy.json");
    }

    public PermissionReviewCatalog(ObjectMapper mapper, String resource) throws IOException {
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            source = stream.readAllBytes();
        }
        policy = mapper.readValue(source, Policy.class);
        Map<String, PolicyEntry> indexed = new LinkedHashMap<>();
        Set<String> roleCodes = Set.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER");
        Set<String> modes = Set.of("ALLOW", "DENY", "OWN", "OVERRIDE", "PUBLIC", "IMMUTABLE");
        Set<String> moduleCodes = new HashSet<>();
        policy.modules().forEach(module -> moduleCodes.add(module.code()));
        for (PolicyEntry entry : policy.entries()) {
            if (indexed.put(entry.id(), entry) != null || !entry.rules().keySet().equals(roleCodes)
                    || !modes.containsAll(entry.rules().values()) || !moduleCodes.contains(entry.module())
                    || entry.httpMethods().isEmpty() || !entry.path().startsWith("/api/v1/")) {
                throw new IllegalStateException("Bảng quyền không hợp lệ: " + entry.id());
            }
        }
        entries = Collections.unmodifiableMap(indexed);
    }

    public Policy policy() { return policy; }
    public byte[] source() { return source.clone(); }
    public Optional<PolicyEntry> find(String id) { return Optional.ofNullable(entries.get(id)); }

    public int requiredStatus(PolicyEntry entry, String role, String scenario, int successStatus) {
        boolean publicApi = entry.rules().values().stream().allMatch("PUBLIC"::equals);
        if ("ANONYMOUS".equals(role) && !publicApi) return 401;
        if (publicApi) {
            if (successStatus < 200 || successStatus >= 300) throw new IllegalArgumentException("Luồng công khai hợp lệ cần kết quả 2xx.");
            return successStatus;
        }
        String mode = entry.rules().getOrDefault(role, "DENY");
        if ("DENY".equals(mode)) return 403;
        if ("IMMUTABLE".equals(mode)) return 405;
        if ("OTHER_OWNER".equals(scenario)) {
            if (!"OWN".equals(mode)) throw new IllegalArgumentException("Không phải luồng sở hữu tài nguyên.");
            return 404;
        }
        if ("OVERRIDE".equals(mode) && !"OVERRIDE".equals(scenario)) return 403;
        if (successStatus < 200 || successStatus >= 300) {
            throw new IllegalArgumentException("Luồng được phép phải có kết quả nghiệp vụ 2xx.");
        }
        return successStatus;
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCoverageService.java

````java
package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class PermissionReviewCoverageService {
    private final PermissionReviewService reviews;
    private final PermissionReviewRepository repository;
    private final UserRepository users;
    public PermissionReviewCoverageService(PermissionReviewService reviews, PermissionReviewRepository repository, UserRepository users) {
        this.reviews=reviews; this.repository=repository; this.users=users;
    }
    @Transactional(readOnly=true)
    public Coverage coverage(Long id) {
        ReportDetail detail=reviews.detail(id); Inspection inspection=reviews.inspect();
        Map<String,CaseResult> results=new HashMap<>();
        for(var result:detail.results()) {
            var test=result.test();
            results.put(key(test.endpointId(),test.httpMethod(),test.role(),test.scenario()),result);
        }
        List<MissingRoleCase> missing=new ArrayList<>(); int covered=0,coveredApis=0;
        for(var row:inspection.entries()) for(String method:row.policy().httpMethods()) {
            boolean apiCovered=true;
            for(var role:inspection.roles()) {
                String scenario="STANDARD";
                if(!results.containsKey(key(row.policy().id(),method,role.code(),scenario))) {
                    missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),scenario));apiCovered=false;
                } else covered++;
                if("OVERRIDE".equals(row.policy().rules().get(role.code())) && !results.containsKey(key(row.policy().id(),method,role.code(),"OVERRIDE")))
                    missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),"OVERRIDE"));
                if("OWN".equals(row.policy().rules().get(role.code()))) {
                    String scope=Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(row.policy().id())?"OTHER_OWNER":"SPOOFED_OWNER";
                    if(!results.containsKey(key(row.policy().id(),method,role.code(),scope))) missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),scope));
                }
            }
            if(apiCovered)coveredApis++;
            if(!results.containsKey(key(row.policy().id(),method,"ANONYMOUS","STANDARD")))missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),"ANONYMOUS","STANDARD"));
        }
        int failed=(int)detail.results().stream().filter(result->!result.passed()).count();
        boolean current=detail.currentFingerprint() && detail.evidence().policyVersion().equals(inspection.policyVersion());
        boolean executed=repository.serverExecuted(id,detail.evidence().runKey(),inspection.fingerprint())
                && detail.evidence().schemaVersion()==2 && "REGRESSION".equals(detail.evidence().kind())
                && "ISOLATED_POSTGRES".equals(detail.evidence().environment());
        List<String> reasons=new ArrayList<>();
        if(!current)reasons.add("Bảng quyền hoặc bản code đã thay đổi; cần chạy lại toàn bộ.");
        if(!executed)reasons.add("Biên bản chưa được xác thực là kết quả bộ chạy toàn hệ thống trên database riêng.");
        if(!missing.isEmpty())reasons.add("Còn "+missing.size()+" trường hợp thiếu kiểm thử vai trò, chưa đăng nhập hoặc phạm vi dữ liệu.");
        if(failed>0)reasons.add("Còn "+failed+" trường hợp kiểm thử chưa đạt.");
        if(inspection.differentFunctions()>0)reasons.add("Ma trận còn "+inspection.differentFunctions()+" chức năng sai lệch hoặc chưa xác định.");
        if(inspection.totalApis()==0)reasons.add("Chưa phát hiện API nghiệp vụ.");
        return new Coverage(inspection.totalApis(),coveredApis,inspection.totalApis()*4,covered,
                detail.results().size()-failed,failed,failed,inspection.differentFunctions(),inspection.unresolvedFunctions(),
                current,executed,reasons.isEmpty(),List.copyOf(missing),List.copyOf(reasons),repository.completion(id).orElse(null));
    }
    @Transactional
    public Coverage complete(Long id, Long actorId) {
        repository.lockReport(id);
        var actor=actorId==null?null:users.findById(actorId).orElse(null);
        if(actor==null || !"ACTIVE".equals(actor.getStatus()) || actor.getRole()==null || !"ADMIN".equals(actor.getRole().getCode()))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ Quản trị hệ thống được xác nhận rà soát.");
        Coverage coverage=coverage(id);
        if(!coverage.eligible())throw new ApiException(HttpStatus.CONFLICT,"PERMISSION_REVIEW_INCOMPLETE",String.join(" ",coverage.reasons()));
        if(coverage.completion()==null)repository.complete(id,actorId,actor.getFullName(),reviews.inspect().fingerprint(),coverage.coveredRoleCases());
        return coverage(id);
    }
    @Transactional(readOnly=true)
    public ExportReport export(Long id) {
        var detail=reviews.detail(id);
        return new ExportReport(detail,coverage(id),repository.sprintReports(detail.evidence().policyVersion()).stream().map(summary -> reviews.detail(summary.id())).toList());
    }
    private String key(String id,String method,String role,String scenario){return id+":"+method+":"+role+":"+scenario;}
}
````

## backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewInspector.java

````java
package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Inspects effective controller gates; HTTP/service/ownership evidence remains a separate report. */
@Component
public class PermissionReviewInspector {
    private final PermissionReviewCatalog catalog;
    private final RequestMappingHandlerMapping mappings;
    private final SecurityFilterChain filterChain;
    private final ServletContext servletContext;
    private final org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
    private static final Pattern ROLE_GUARD = Pattern.compile("has(?:Any)?Role\\(\\s*'[^']+'(?:\\s*,\\s*'[^']+')*\\s*\\)");
    private static final Pattern ROLE_VALUE = Pattern.compile("'([^']+)'");

    public PermissionReviewInspector(PermissionReviewCatalog catalog,
            @Lazy @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            @Lazy @Qualifier("securityFilterChain") SecurityFilterChain filterChain,
            ServletContext servletContext) {
        this.catalog = catalog;
        this.mappings = mappings;
        this.filterChain = filterChain;
        this.servletContext = servletContext;
    }

    public Inspection inspect() {
        List<ReviewedEntry> reviewed = new ArrayList<>();
        for (PolicyEntry entry : catalog.policy().entries()) {
            List<HandlerMethod> handlers = mappings.getHandlerMethods().entrySet().stream()
                    .filter(item -> item.getKey().getPatternValues().contains(entry.path()))
                    .filter(item -> item.getKey().getMethodsCondition().getMethods().stream()
                            .map(Enum::name).collect(java.util.stream.Collectors.toSet()).containsAll(entry.httpMethods()))
                    .map(Map.Entry::getValue).toList();
            boolean mapped = handlers.size() == 1
                    && handlers.get(0).getBeanType().getSimpleName().equals(entry.controller())
                    && handlers.get(0).getMethod().getName().equals(entry.handler());
            String guard = mapped ? effectiveGuard(handlers.get(0)) : "MISSING_OR_AMBIGUOUS_MAPPING";
            List<RoleComparison> roles = catalog.policy().roles().stream().map(role -> {
                String methodGate = mapped ? gate(guard, role.code()) : "UNKNOWN";
                String urlGate = mapped ? urlGate(entry, role.code()) : "UNKNOWN";
                String actual = "DENY".equals(urlGate) ? "DENY"
                        : "UNKNOWN".equals(urlGate) ? "UNKNOWN" : methodGate;
                String documented = entry.rules().get(role.code());
                boolean matches = !"UNKNOWN".equals(actual)
                        && ("DENY".equals(documented) ? "DENY".equals(actual) : "ALLOW".equals(actual));
                return new RoleComparison(role.code(), documented, actual, matches);
            }).toList();
            reviewed.add(new ReviewedEntry(entry, guard, roles, mapped,
                    roles.stream().anyMatch(role -> !role.matches())));
        }
        List<String> undocumented = new ArrayList<>();
        if (catalog.policy().version().startsWith("S4-09.2")) {
            for (var mapping : mappings.getHandlerMethods().entrySet()) {
                if (!mapping.getValue().getBeanType().getName().startsWith("com.duanttcsn5.library.")) continue;
                for (String path : mapping.getKey().getPatternValues()) {
                    if (!path.startsWith("/api/v1/")) continue;
                    for (var verb : mapping.getKey().getMethodsCondition().getMethods()) {
                        boolean documented = catalog.policy().entries().stream().anyMatch(e -> e.path().equals(path)
                                && e.httpMethods().contains(verb.name())
                                && e.controller().equals(mapping.getValue().getBeanType().getSimpleName())
                                && e.handler().equals(mapping.getValue().getMethod().getName()));
                        if (!documented) {
                            String id = mapping.getValue().getBeanType().getSimpleName() + "." + mapping.getValue().getMethod().getName()
                                    + ":" + verb.name() + ":" + path;
                            undocumented.add(verb.name() + " " + path);
                            Map<String, String> unknownRules = new LinkedHashMap<>();
                            catalog.policy().roles().forEach(role -> unknownRules.put(role.code(), "UNKNOWN"));
                            var policy = new PolicyEntry(id, "UNDOCUMENTED", "API chưa có trong tài liệu quyền",
                                    mapping.getValue().getBeanType().getSimpleName(), mapping.getValue().getMethod().getName(),
                                    path, List.of(verb.name()), unknownRules, "Cần PO quy định quyền và bổ sung kiểm thử trước khi hoàn tất.",
                                    !verb.name().equals("GET"));
                            String guard = effectiveGuard(mapping.getValue());
                            reviewed.add(new ReviewedEntry(policy, guard, catalog.policy().roles().stream()
                                    .map(role -> new RoleComparison(role.code(), "UNKNOWN", gate(guard, role.code()), false)).toList(), true, true));
                        }
                    }
                }
            }
        }
        List<ModuleOption> modules = new ArrayList<>(catalog.policy().modules());
        if (!undocumented.isEmpty()) modules.add(new ModuleOption("UNDOCUMENTED", "Chưa có tài liệu quyền", 99));
        return new Inspection(catalog.policy().version(), catalog.policy().decision(), fingerprint(reviewed),
                OffsetDateTime.now(), catalog.policy().roles(), modules, reviewed.size(),
                (int) reviewed.stream().filter(ReviewedEntry::different).count(),
                (int) reviewed.stream().filter(entry -> entry.roles().stream()
                        .anyMatch(role -> "UNKNOWN".equals(role.actualGate()) || "UNKNOWN".equals(role.documented()))).count(), reviewed,
                reviewed.stream().mapToInt(e -> e.policy().httpMethods().size()).sum(), List.copyOf(undocumented));
    }

    private String effectiveGuard(HandlerMethod handler) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        return annotation == null ? "AUTHENTICATED_ONLY" : annotation.value();
    }

    static String gate(String guard, String role) {
        if ("AUTHENTICATED_ONLY".equals(guard)) return "ALLOW";
        if (!ROLE_GUARD.matcher(guard.trim()).matches()) return "UNKNOWN";
        var matcher = ROLE_VALUE.matcher(guard);
        while (matcher.find()) if (role.equals(matcher.group(1))) return "ALLOW";
        return "DENY";
    }

    private String urlGate(PolicyEntry entry, String role) {
        try {
            AuthorizationFilter filter = filterChain.getFilters().stream().filter(AuthorizationFilter.class::isInstance)
                    .map(AuthorizationFilter.class::cast).findFirst().orElse(null);
            if (filter == null) return "UNKNOWN";
            var authentication = new UsernamePasswordAuthenticationToken("permission-inspection", null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            for (String verb : entry.httpMethods()) {
                HttpServletRequest request = inspectionRequest(entry.path().replaceAll("\\{[^}]+}", "1"), verb);
                var decision = filter.getAuthorizationManager().authorize(() -> authentication, request);
                if (decision == null) return "UNKNOWN";
                if (!decision.isGranted()) return "DENY";
            }
            return "ALLOW";
        } catch (RuntimeException exception) {
            return "UNKNOWN";
        }
    }

    /** Synthetic read-only request for Spring's real URL authorization manager, without dispatching an API. */
    private HttpServletRequest inspectionRequest(String path, String verb) {
        Map<String, Object> attributes = new HashMap<>();
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> verb;
                    case "getRequestURI", "getServletPath" -> path;
                    case "getContextPath" -> "";
                    case "getPathInfo" -> null;
                    case "getHttpServletMapping" -> new jakarta.servlet.http.HttpServletMapping() {
                        public String getMatchValue() { return ""; }
                        public String getPattern() { return "/"; }
                        public String getServletName() { return "dispatcherServlet"; }
                        public jakarta.servlet.http.MappingMatch getMappingMatch() { return jakarta.servlet.http.MappingMatch.DEFAULT; }
                    };
                    case "getRequestURL" -> new StringBuffer("http://localhost" + path);
                    case "getDispatcherType" -> DispatcherType.REQUEST;
                    case "getServletContext" -> servletContext;
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                    case "removeAttribute" -> { attributes.remove((String) args[0]); yield null; }
                    case "getAttributeNames" -> Collections.enumeration(attributes.keySet());
                    case "toString" -> verb + " " + path;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
                });
    }

    private String fingerprint(List<ReviewedEntry> reviewed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(catalog.source());
            for (ReviewedEntry entry : reviewed) {
                digest.update((entry.policy().id() + ":" + entry.mapped() + ":" + entry.guard()).getBytes(StandardCharsets.UTF_8));
            }
            Map<String, org.springframework.core.io.Resource> resources = new TreeMap<>();
            for (String area : List.of("controller", "service", "security", "repository", "dto", "config", "entity")) {
                for (var resource : resolver.getResources("classpath*:com/duanttcsn5/library/" + area + "/**/*.class")) {
                    String uri = resource.getURI().toString();
                    String key = uri.substring(uri.indexOf("com/duanttcsn5/library/"));
                    if (uri.contains("test-classes")) continue;
                    resources.putIfAbsent(key, resource);
                }
            }
            for (String pattern : List.of("classpath*:db/migration/*.sql", "classpath*:permissions/S4-09.2-fixes.json")) {
                for (var resource : resolver.getResources(pattern)) resources.put("resource/"+resource.getFilename(),resource);
            }
            for (var resource : resources.entrySet()) {
                digest.update(resource.getKey().getBytes(StandardCharsets.UTF_8));
                try (var stream = resource.getValue().getInputStream()) { digest.update(stream.readAllBytes()); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được dấu vân tay phân quyền.", exception);
        }
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewRunService.java

````java
package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.net.URI;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;

/** Local source workflow: fixed Maven target, separate PostgreSQL database, no live business probes. */
@Service
public class PermissionReviewRunService {
    private final PermissionReviewRepository repository;
    private final PermissionReviewService reviews;
    private final PermissionReviewInspector inspector;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final String databaseUrl, username, password;
    private final boolean launchEnabled;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"permission-review-runner");t.setDaemon(true);return t;});
    private volatile Process child;

    public PermissionReviewRunService(PermissionReviewRepository repository, PermissionReviewService reviews,
            PermissionReviewInspector inspector, UserRepository users, ObjectMapper mapper, Validator validator,
            @Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${app.permission-review.launch-enabled:true}") boolean launchEnabled) {
        this.repository=repository;this.reviews=reviews;this.inspector=inspector;this.users=users;
        this.mapper=mapper;this.validator=validator;this.databaseUrl=url;this.username=username;this.password=password;this.launchEnabled=launchEnabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() { if(launchEnabled)repository.recoverInterruptedJobs(); }

    @PreAuthorize("hasRole('ADMIN')")
    public RunState start(Long actorId) {
        var actor=actorId==null?null:users.findById(actorId).orElse(null);
        if(actor==null || !"ACTIVE".equals(actor.getStatus()) || actor.getRole()==null || !"ADMIN".equals(actor.getRole().getCode()))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ Quản trị hệ thống được chạy kiểm thử.");
        if(launchEnabled)backendDirectory();
        UUID key=UUID.randomUUID(); String fingerprint=inspector.inspect().fingerprint();
        try { repository.createJob(key,actorId,actor.getFullName(),fingerprint); }
        catch(DuplicateKeyException exception) { throw new ApiException(HttpStatus.CONFLICT,"PERMISSION_RUN_ACTIVE","Một lần kiểm thử đang chạy. Vui lòng chờ kết quả."); }
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(launchEnabled)executor.submit(()->execute(key,actorId,fingerprint,authentication));
        return get(key);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public RunState get(UUID key) {return repository.job(key).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PERMISSION_RUN_NOT_FOUND","Không tìm thấy lần kiểm thử."));}
    @PreAuthorize("hasRole('ADMIN')")
    public RunState latest() { return repository.latestJob().orElse(null); }

    private void execute(UUID key,Long actor,String fingerprint,Authentication authentication) {
        var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(authentication);SecurityContextHolder.setContext(security);
        boolean databaseCreated=false;
        Long reportId=null; String testDatabase="s4092_review_"+key.toString().replace("-","");
        try {
            Path backend=backendDirectory(); Path directory=backend.resolve("target/permission-review/jobs");Files.createDirectories(directory);
            Path output=directory.resolve(key+".json");Path log=directory.resolve(key+".log");
            prepareDatabase(testDatabase);databaseCreated=true;
            List<String> command=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ?List.of("cmd.exe","/d","/c","mvnw.cmd","-B","-Dtest=FullPermissionDatabaseTest","test")
                    :List.of("sh","./mvnw","-B","-Dtest=FullPermissionDatabaseTest","test");
            ProcessBuilder builder=new ProcessBuilder(command).directory(backend.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            Map<String,String> env=builder.environment();
            env.put("S4_09_2_DB_TEST","true");env.put("DB_NAME",testDatabase);
            env.put("DB_HOST","127.0.0.1");env.put("DB_PORT","5433");env.put("DB_USERNAME",username);env.put("DB_PASSWORD",password);
            env.put("PERMISSION_REVIEW_RUN_KEY",key.toString());env.put("PERMISSION_REVIEW_RESULT",output.toString());
            env.put("PERMISSION_REVIEW_LIVE_DB",liveDatabase());
            env.put("BOOTSTRAP_ADMIN_PASSWORD","");env.put("MAIL_USERNAME","");env.put("MAIL_PASSWORD","");env.put("MAIL_HOST","127.0.0.1");
            repository.running(key);child=builder.start();
            boolean done=child.waitFor(15,TimeUnit.MINUTES);
            if(!done){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();throw new IllegalStateException("timeout");}
            int exit=child.exitValue();child=null;
            if(Files.exists(output) && Files.size(output)<=16*1024*1024) {
                ImportReport report=mapper.readValue(Files.readString(output),ImportReport.class);
                if(!validator.validate(report).isEmpty() || !key.equals(report.runKey()) || !fingerprint.equals(report.fingerprint())
                        || report.schemaVersion()!=2 || !"ISOLATED_POSTGRES".equals(report.environment()) || !"REGRESSION".equals(report.kind()))
                    throw new IllegalStateException("invalid worker evidence");
                reportId=reviews.importReport(report,actor).summary().id();
            }
            if(exit!=0 || reportId==null)repository.finishJob(key,"FAILED","Bộ kiểm thử chưa đạt hoặc chưa tạo được biên bản. Xem kết quả đã lưu; log chi tiết nằm trong backend/target/permission-review/jobs.",reportId);
            else repository.finishJob(key,"SUCCEEDED","Đã chạy xong. Mở biên bản để xem độ bao phủ và điều kiện hoàn tất.",reportId);
        } catch(Exception exception) {
            if(exception instanceof InterruptedException)Thread.currentThread().interrupt();
            repository.finishJob(key,"FAILED","Chưa chạy xong kiểm thử. Kiểm tra PostgreSQL 5433, quyền tạo database thử, JDK 17 và Maven Wrapper; xem hướng dẫn S4-09.2.",reportId);
        } finally {
            if(databaseCreated)try { dropDatabase(testDatabase); } catch(Exception cleanupFailure) { repository.cleanupWarning(key); }
            SecurityContextHolder.clearContext();
        }
    }
    private Path backendDirectory() {
        Path current=Path.of("").toAbsolutePath().normalize();
        Path backend=Files.isRegularFile(current.resolve("pom.xml"))?current:current.resolve("backend");
        if(!Files.isRegularFile(backend.resolve("mvnw.cmd")) || !Files.isRegularFile(backend.resolve("src/test/java/com/duanttcsn5/library/FullPermissionDatabaseTest.java")))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PERMISSION_RUN_SOURCE_REQUIRED","Hãy chạy từ source bằng RUN_ALL.cmd; bộ chạy cần Maven Wrapper và file kiểm thử S4-09.2.");
        return backend;
    }
    private String liveDatabase() {return URI.create(databaseUrl.substring(5)).getPath().substring(1);}
    private void prepareDatabase(String testDatabase) throws Exception {
        URI uri=URI.create(databaseUrl.substring(5));
        if(!List.of("127.0.0.1","localhost","[::1]","::1").contains(uri.getHost()) || uri.getPort()!=5433
                || liveDatabase().equals(testDatabase) || !testDatabase.matches("s4092_review_[a-f0-9]{32}")) throw new IllegalStateException("Requires separate local PostgreSQL 5433 database");
        String url="jdbc:postgresql://127.0.0.1:5433/postgres";
        try(var connection=DriverManager.getConnection(url,username,password)) {
            boolean exists;
            try(var query=connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                query.setString(1,testDatabase);try(var rows=query.executeQuery()){exists=rows.next();}
            }
            if(exists)throw new IllegalStateException("Test database name is already in use");
            try(var statement=connection.createStatement()){statement.execute("CREATE DATABASE "+testDatabase+" TEMPLATE template0");}
        }
    }
    private void dropDatabase(String name) throws Exception {
        if(!name.matches("s4092_review_[a-f0-9]{32}") || name.equals(liveDatabase()))throw new IllegalStateException("Invalid test database");
        try(var connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5433/postgres",username,password);var statement=connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS "+name+" WITH (FORCE)");
        }
    }
    @PreDestroy public void close() {
        executor.shutdownNow();Process process=child;
        if(process!=null){process.descendants().forEach(ProcessHandle::destroy);process.destroy();}
    }
}
````

## backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewService.java

````java
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
````

## backend/src/main/resources/db/migration/V59__complete_permission_review.sql

````sql
-- S4-09.2: server-run provenance and immutable completion; no business fixtures.
CREATE TABLE permission_review_jobs (
    run_key UUID PRIMARY KEY,
    state VARCHAR(20) NOT NULL CHECK (state IN ('STARTING','RUNNING','SUCCEEDED','FAILED')),
    message VARCHAR(500) NOT NULL,
    requested_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    requested_by_name VARCHAR(255) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    report_id BIGINT UNIQUE REFERENCES permission_review_runs(id),
    CHECK ((state IN ('STARTING','RUNNING') AND finished_at IS NULL)
        OR (state IN ('SUCCEEDED','FAILED') AND finished_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_permission_review_one_active_job ON permission_review_jobs ((true))
    WHERE state IN ('STARTING','RUNNING');
CREATE TABLE permission_review_completions (
    report_id BIGINT PRIMARY KEY REFERENCES permission_review_runs(id),
    confirmed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    confirmed_by_name VARCHAR(255) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fingerprint CHAR(64) NOT NULL,
    role_case_count INTEGER NOT NULL CHECK (role_case_count > 0)
);
CREATE FUNCTION reject_permission_completion_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.confirmed_by_user_id IS NOT NULL AND NEW.confirmed_by_user_id IS NULL
       AND (to_jsonb(NEW) - 'confirmed_by_user_id') = (to_jsonb(OLD) - 'confirmed_by_user_id') THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Permission review completion is immutable';
END;
$$;
CREATE TRIGGER protect_permission_review_completion BEFORE UPDATE OR DELETE ON permission_review_completions
    FOR EACH ROW EXECUTE FUNCTION reject_permission_completion_change();
````

## backend/src/main/resources/db/migration/V60__allow_authorized_incident_adjustment_confirmation.sql

````sql
-- S4-09.2 dependency: valid ADMIN/MANAGER confirmation failed in PostgreSQL 15.
-- adjustment_number is GENERATED ALWAYS AS from id and is NULL in NEW during BEFORE UPDATE.
-- Exclude that generated value from the immutable request comparison. id and all request fields
-- remain compared; all actor, snapshot, amount and history guards remain unchanged.
CREATE OR REPLACE FUNCTION guard_incident_adjustment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE i book_incidents%ROWTYPE; f reader_fees%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' OR (TG_OP='UPDATE' AND OLD.status<>'PENDING') THEN
  RAISE EXCEPTION 'Không được sửa hoặc xóa lịch sử điều chỉnh.';
 END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NOT EXISTS(SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
   WHERE u.id=NEW.requested_by AND u.status='ACTIVE' AND r.code IN ('LIBRARIAN','LIBRARY_MANAGER','ADMIN')
    AND u.full_name=NEW.requester_name) THEN RAISE EXCEPTION 'Người lập bút toán không hợp lệ.'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.status<>'CONFIRMED' OR (to_jsonb(NEW)-ARRAY['adjustment_number','status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
  'reversed_vnd','cancelled_debt_vnd','refund_due_vnd','before_data','after_data'])
 <> (to_jsonb(OLD)-ARRAY['adjustment_number','status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
  'reversed_vnd','cancelled_debt_vnd','refund_due_vnd','before_data','after_data']) THEN
  RAISE EXCEPTION 'Chỉ được xác nhận yêu cầu, không được sửa nội dung đã lập.';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=NEW.confirmed_by
  AND u.status='ACTIVE' AND r.code IN ('LIBRARY_MANAGER','ADMIN') AND u.full_name=NEW.confirmer_name) THEN
  RAISE EXCEPTION 'Chỉ Quản lý hoặc Admin đang hoạt động được xác nhận.';
 END IF;
 SELECT * INTO STRICT i FROM book_incidents WHERE id=NEW.incident_id;
 SELECT * INTO STRICT f FROM reader_fees WHERE id=i.compensation_fee_id FOR UPDATE;
 IF NEW.before_data->'item' IS DISTINCT FROM (SELECT to_jsonb(li) FROM loan_items li WHERE li.id=i.item_id)
 OR NEW.before_data->'copy' IS DISTINCT FROM (SELECT to_jsonb(c) FROM book_copies c WHERE c.id=i.copy_id)
 OR NEW.before_data->'fee' IS DISTINCT FROM to_jsonb(f) THEN
  RAISE EXCEPTION 'Ảnh chụp dữ liệu trước điều chỉnh không khớp dữ liệu gốc.';
 END IF;
 IF f.waived_amount_vnd<>0 THEN RAISE EXCEPTION 'Khoản bồi thường có miễn giảm không phù hợp quy tắc đảo toàn bộ.'; END IF;
 IF NEW.confirmed_at IS NULL OR NEW.confirmed_at<OLD.requested_at OR NEW.confirmed_at>clock_timestamp() THEN
  RAISE EXCEPTION 'Thời điểm xác nhận không hợp lệ.';
 END IF;
 NEW.confirmed_txid:=txid_current();
 NEW.reversed_vnd:=i.compensation_vnd; NEW.cancelled_debt_vnd:=f.amount_vnd-f.paid_amount_vnd;
 NEW.refund_due_vnd:=f.paid_amount_vnd;
 RETURN NEW;
END; $$;
````

## backend/src/main/resources/permissions/S4-09.2-fixes.json

````json
{
  "sourceReference": "DuAnTTCS_N5(7).zip; baseline chạy thật trên database PostgreSQL 15 tạm riêng. Không dùng dữ liệu mô phỏng.",
  "originalControllerSha256": {
    "backend/src/main/java/com/duanttcsn5/library/controller/AuthorController.java": "4792cc44c70bf23614185b69687e8dd12243415b8a02a364b28e1dce24b3abfa",
    "backend/src/main/java/com/duanttcsn5/library/controller/CategoryController.java": "1d8916a90f1f28a79c8593ce80fc7a9ac933dbed876341777b89ca7f4fd73888",
    "backend/src/main/java/com/duanttcsn5/library/controller/CardTypeController.java": "e259665781af7e86d7a8b51c4699d1071244b8313e6ef2c36e9025aa61c58527"
  },
  "findings": [
    {
      "endpointId": "AuthorController.getAllAuthors",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:15.801415899+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "AuthorController.getActiveAuthors",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:16.030723619+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "AuthorController.getAuthorById",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:16.198823454+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CardTypeController.getAllCardTypes",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:16.455566405+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CardTypeController.getActiveCardTypes",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:16.681018273+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CardTypeController.getCardTypeById",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:16.848134129+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CategoryController.getAllCategories",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:17.139234402+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CategoryController.getActiveCategories",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:17.392592438+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "CategoryController.getCategoryById",
      "httpMethod": "GET",
      "role": "READER",
      "observedStatus": 200,
      "expectedStatus": 403,
      "verifiedAt": "2026-10-15T16:36:17.560214082+08:00",
      "baselineFingerprint": "ba88939ec9916e58a5155228edbd95475a3db3acab5cbd7ca621371ba9741a45",
      "correction": "Bổ sung @PreAuthorize cho API đọc danh mục theo bảng quyền PO S4-09.2; kiểm thử lại bằng JWT Bạn đọc thật."
    },
    {
      "endpointId": "BookIncidentController.confirmAdjustment",
      "httpMethod": "POST",
      "role": "ADMIN",
      "observedStatus": 500,
      "expectedStatus": 200,
      "verifiedAt": "2026-10-15T16:33:10.547984806+08:00",
      "baselineFingerprint": "6708913a925acdf9f25ea6d1c0fa4317a29b5977eff3df70f5a6def526b7fee7",
      "correction": "V60 giữ toàn bộ guard nghiệp vụ, loại cột GENERATED adjustment_number khỏi so sánh BEFORE UPDATE để Admin/Quản lý xác nhận hợp lệ. Đồng bộ updated_at trong ảnh chụp dự kiến với trigger cập nhật bản sao; không bỏ kiểm tra ảnh chụp sau giao dịch."
    },
    {
      "endpointId": "BookIncidentController.confirmAdjustment",
      "httpMethod": "POST",
      "role": "LIBRARY_MANAGER",
      "observedStatus": 500,
      "expectedStatus": 200,
      "verifiedAt": "2026-10-15T16:33:10.646225382+08:00",
      "baselineFingerprint": "6708913a925acdf9f25ea6d1c0fa4317a29b5977eff3df70f5a6def526b7fee7",
      "correction": "V60 giữ toàn bộ guard nghiệp vụ, loại cột GENERATED adjustment_number khỏi so sánh BEFORE UPDATE để Admin/Quản lý xác nhận hợp lệ. Đồng bộ updated_at trong ảnh chụp dự kiến với trigger cập nhật bản sao; không bỏ kiểm tra ảnh chụp sau giao dịch."
    }
  ]
}
````

## backend/src/main/resources/permissions/S4-09.2-policy.json

````json
{
  "version": "S4-09.2-v1",
  "decision": "PO chốt ngày 15/10/2026: mở rộng toàn hệ thống; Admin tách nhiệm vụ; giữ API chưa có UI; database thử riêng PostgreSQL 5433. S4-09.1 giữ nguyên.",
  "roles": [
    {
      "code": "ADMIN",
      "name": "Quản trị hệ thống"
    },
    {
      "code": "LIBRARY_MANAGER",
      "name": "Quản lý thư viện"
    },
    {
      "code": "LIBRARIAN",
      "name": "Thủ thư"
    },
    {
      "code": "READER",
      "name": "Bạn đọc"
    }
  ],
  "modules": [
    {
      "code": "FEES",
      "name": "Thu phí và miễn giảm",
      "priority": 1
    },
    {
      "code": "READERS",
      "name": "Bạn đọc và thẻ",
      "priority": 2
    },
    {
      "code": "LOANS",
      "name": "Mượn và trả sách",
      "priority": 3
    },
    {
      "code": "STOCK",
      "name": "Kho và sự cố sách",
      "priority": 4
    },
    {
      "code": "RESERVATIONS",
      "name": "Đặt giữ",
      "priority": 5
    },
    {
      "code": "ACCOUNTS",
      "name": "Tài khoản nhân sự",
      "priority": 6
    },
    {
      "code": "AUDIT",
      "name": "Nhật ký hoạt động",
      "priority": 7
    },
    {
      "code": "CATALOG",
      "name": "Danh mục và biên mục",
      "priority": 8
    },
    {
      "code": "CONFIG",
      "name": "Chính sách và cấu hình",
      "priority": 9
    },
    {
      "code": "EMAIL",
      "name": "Email nhắc và thông báo",
      "priority": 10
    },
    {
      "code": "REPORTS",
      "name": "Thống kê và báo cáo",
      "priority": 11
    },
    {
      "code": "AUTH",
      "name": "Xác thực và tài khoản cá nhân",
      "priority": 12
    },
    {
      "code": "PUBLIC",
      "name": "Tra cứu và đăng ký công khai",
      "priority": 13
    },
    {
      "code": "REVIEW",
      "name": "Rà soát phân quyền",
      "priority": 14
    }
  ],
  "entries": [
    {
      "id": "ReaderController.getMyProfile",
      "module": "READERS",
      "label": "Xem hồ sơ cá nhân",
      "controller": "ReaderController",
      "handler": "getMyProfile",
      "path": "/api/v1/readers/me/profile",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "ReaderController.updateMyContact",
      "module": "READERS",
      "label": "Cập nhật liên hệ cá nhân",
      "controller": "ReaderController",
      "handler": "updateMyContact",
      "path": "/api/v1/readers/me/contact",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "ReaderController.changeMyPassword",
      "module": "READERS",
      "label": "Đổi mật khẩu cá nhân",
      "controller": "ReaderController",
      "handler": "changeMyPassword",
      "path": "/api/v1/readers/me/password",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "ReaderController.getAllReaders",
      "module": "READERS",
      "label": "Xem danh sách bạn đọc",
      "controller": "ReaderController",
      "handler": "getAllReaders",
      "path": "/api/v1/readers",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "ReaderController.getReaderById",
      "module": "READERS",
      "label": "Xem hồ sơ bạn đọc",
      "controller": "ReaderController",
      "handler": "getReaderById",
      "path": "/api/v1/readers/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "ReaderController.getReaderLoanHistory",
      "module": "READERS",
      "label": "Xem lịch sử mượn của bạn đọc",
      "controller": "ReaderController",
      "handler": "getReaderLoanHistory",
      "path": "/api/v1/readers/{id}/loan-history",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "ReaderController.exportReaderLoanHistory",
      "module": "READERS",
      "label": "Xuất lịch sử mượn của bạn đọc",
      "controller": "ReaderController",
      "handler": "exportReaderLoanHistory",
      "path": "/api/v1/readers/{id}/loan-history/export",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.lockSuggestions",
      "module": "READERS",
      "label": "Xem đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "lockSuggestions",
      "path": "/api/v1/library-cards/lock-suggestions",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.suggestionDetail",
      "module": "READERS",
      "label": "Xem chi tiết đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "suggestionDetail",
      "path": "/api/v1/library-cards/lock-suggestions/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.acceptSuggestion",
      "module": "READERS",
      "label": "Chấp nhận đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "acceptSuggestion",
      "path": "/api/v1/library-cards/lock-suggestions/{id}/accept",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.ignoreSuggestion",
      "module": "READERS",
      "label": "Bỏ qua đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "ignoreSuggestion",
      "path": "/api/v1/library-cards/lock-suggestions/{id}/ignore",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.getPendingApplications",
      "module": "READERS",
      "label": "Xem hồ sơ chờ duyệt",
      "controller": "LibraryCardController",
      "handler": "getPendingApplications",
      "path": "/api/v1/library-cards/pending",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.getIssuedCards",
      "module": "READERS",
      "label": "Xem danh sách thẻ",
      "controller": "LibraryCardController",
      "handler": "getIssuedCards",
      "path": "/api/v1/library-cards",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.approve",
      "module": "READERS",
      "label": "Duyệt hồ sơ và cấp thẻ",
      "controller": "LibraryCardController",
      "handler": "approve",
      "path": "/api/v1/library-cards/{readerUserId}/approve",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.reject",
      "module": "READERS",
      "label": "Từ chối hồ sơ",
      "controller": "LibraryCardController",
      "handler": "reject",
      "path": "/api/v1/library-cards/{readerUserId}/reject",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.lockCard",
      "module": "READERS",
      "label": "Khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "lockCard",
      "path": "/api/v1/library-cards/{cardId}/lock",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.getCardDetails",
      "module": "READERS",
      "label": "Xem chi tiết thẻ",
      "controller": "LibraryCardController",
      "handler": "getCardDetails",
      "path": "/api/v1/library-cards/{cardId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LibraryCardController.unlockCard",
      "module": "READERS",
      "label": "Mở khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "unlockCard",
      "path": "/api/v1/library-cards/{cardId}/unlock",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryCardController.getMyCard",
      "module": "READERS",
      "label": "Xem thẻ cá nhân",
      "controller": "LibraryCardController",
      "handler": "getMyCard",
      "path": "/api/v1/library-cards/me",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.pickupContext",
      "module": "LOANS",
      "label": "Xem ngữ cảnh nhận sách từ đặt giữ",
      "controller": "LoanController",
      "handler": "pickupContext",
      "path": "/api/v1/reservations/{reservationId}/loan-context",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.checkPickup",
      "module": "LOANS",
      "label": "Kiểm tra nhận sách tại quầy",
      "controller": "LoanController",
      "handler": "checkPickup",
      "path": "/api/v1/reservations/{reservationId}/pickup-check",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LoanController.createFromReservation",
      "module": "LOANS",
      "label": "Tạo phiếu từ một đơn đặt giữ",
      "controller": "LoanController",
      "handler": "createFromReservation",
      "path": "/api/v1/reservations/{reservationId}/loan",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "OVERRIDE",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ.",
      "mutating": true
    },
    {
      "id": "LoanController.createFromReservations",
      "module": "LOANS",
      "label": "Tạo phiếu từ nhiều đơn đặt giữ",
      "controller": "LoanController",
      "handler": "createFromReservations",
      "path": "/api/v1/reservations/loans",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "OVERRIDE",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ.",
      "mutating": true
    },
    {
      "id": "LoanController.myBorrowedBooks",
      "module": "LOANS",
      "label": "Xem sách đang mượn cá nhân",
      "controller": "LoanController",
      "handler": "myBorrowedBooks",
      "path": "/api/v1/loans/me/borrowed-books",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.checkMyRenewal",
      "module": "LOANS",
      "label": "Kiểm tra và xử lý gia hạn cá nhân",
      "controller": "LoanController",
      "handler": "checkMyRenewal",
      "path": "/api/v1/loans/me/borrowed-books/{itemId}/renewal-check",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LoanController.myReturnedBooks",
      "module": "LOANS",
      "label": "Xem sách đã trả cá nhân",
      "controller": "LoanController",
      "handler": "myReturnedBooks",
      "path": "/api/v1/loans/me/returned-books",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.searchLoans",
      "module": "LOANS",
      "label": "Tìm phiếu mượn",
      "controller": "LoanController",
      "handler": "searchLoans",
      "path": "/api/v1/loans/search",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.overdueLoans",
      "module": "LOANS",
      "label": "Xem sách quá hạn",
      "controller": "LoanController",
      "handler": "overdueLoans",
      "path": "/api/v1/loans/overdue",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.listLoans",
      "module": "LOANS",
      "label": "Xem danh sách phiếu mượn",
      "controller": "LoanController",
      "handler": "listLoans",
      "path": "/api/v1/loans",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.readerEligibility",
      "module": "LOANS",
      "label": "Xem điều kiện mượn",
      "controller": "LoanController",
      "handler": "readerEligibility",
      "path": "/api/v1/loans/reader-eligibility",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.checkReaderAndLog",
      "module": "LOANS",
      "label": "Kiểm tra điều kiện và ghi nhận từ chối",
      "controller": "LoanController",
      "handler": "checkReaderAndLog",
      "path": "/api/v1/loans/reader-eligibility/check",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LoanController.rejections",
      "module": "LOANS",
      "label": "Xem nhật ký từ chối mượn",
      "controller": "LoanController",
      "handler": "rejections",
      "path": "/api/v1/loans/rejections",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.rejectionDetail",
      "module": "LOANS",
      "label": "Xem chi tiết từ chối mượn",
      "controller": "LoanController",
      "handler": "rejectionDetail",
      "path": "/api/v1/loans/rejections/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.previewDirectLoanItem",
      "module": "LOANS",
      "label": "Kiểm tra bản sách mượn trực tiếp",
      "controller": "LoanController",
      "handler": "previewDirectLoanItem",
      "path": "/api/v1/loans/direct/items/preview",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "OVERRIDE",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ.",
      "mutating": true
    },
    {
      "id": "LoanController.createDirectLoan",
      "module": "LOANS",
      "label": "Tạo phiếu mượn trực tiếp",
      "controller": "LoanController",
      "handler": "createDirectLoan",
      "path": "/api/v1/loans/direct",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "OVERRIDE",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ.",
      "mutating": true
    },
    {
      "id": "LoanController.lookupReturn",
      "module": "LOANS",
      "label": "Tra cứu tiếp nhận trả sách",
      "controller": "LoanController",
      "handler": "lookupReturn",
      "path": "/api/v1/loans/return-lookup",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "LoanController.confirmReturn",
      "module": "LOANS",
      "label": "Xác nhận trả sách",
      "controller": "LoanController",
      "handler": "confirmReturn",
      "path": "/api/v1/loans/return-confirmation",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LoanController.loanDetail",
      "module": "LOANS",
      "label": "Xem chi tiết phiếu mượn",
      "controller": "LoanController",
      "handler": "loanDetail",
      "path": "/api/v1/loans/{loanId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "OWN"
      },
      "condition": "Bạn đọc chỉ xem được phiếu thuộc chính mình; tài nguyên khác được che bằng HTTP 404.",
      "mutating": false
    },
    {
      "id": "BookReservationController.getMine",
      "module": "RESERVATIONS",
      "label": "Xem đơn đặt giữ cá nhân",
      "controller": "BookReservationController",
      "handler": "getMine",
      "path": "/api/v1/reservations/mine",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.cancelMine",
      "module": "RESERVATIONS",
      "label": "Hủy đơn đặt giữ cá nhân",
      "controller": "BookReservationController",
      "handler": "cancelMine",
      "path": "/api/v1/reservations/mine/{reservationId}/cancel",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.reserve",
      "module": "RESERVATIONS",
      "label": "Đặt giữ một đầu sách",
      "controller": "BookReservationController",
      "handler": "reserve",
      "path": "/api/v1/books/{bookId}/reservations",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.reserveMany",
      "module": "RESERVATIONS",
      "label": "Đặt giữ nhiều bản",
      "controller": "BookReservationController",
      "handler": "reserveMany",
      "path": "/api/v1/books/{bookId}/reservations/bulk",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "OWN"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.getQueueByBookId",
      "module": "RESERVATIONS",
      "label": "Xem hàng đợi đặt giữ",
      "controller": "BookReservationController",
      "handler": "getQueueByBookId",
      "path": "/api/v1/books/{bookId}/reservations",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.cancelByStaff",
      "module": "RESERVATIONS",
      "label": "Hủy đơn đặt giữ tại quầy",
      "controller": "BookReservationController",
      "handler": "cancelByStaff",
      "path": "/api/v1/reservations/{reservationId}/cancel",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.getReadyForPickup",
      "module": "RESERVATIONS",
      "label": "Xem đơn sẵn sàng nhận",
      "controller": "BookReservationController",
      "handler": "getReadyForPickup",
      "path": "/api/v1/reservations/ready-for-pickup",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.getReadyForPickupById",
      "module": "RESERVATIONS",
      "label": "Xem chi tiết đơn sẵn sàng nhận",
      "controller": "BookReservationController",
      "handler": "getReadyForPickupById",
      "path": "/api/v1/reservations/ready-for-pickup/{reservationId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.autoCancelOverdue",
      "module": "RESERVATIONS",
      "label": "Xử lý đơn đặt giữ quá hạn",
      "controller": "BookReservationController",
      "handler": "autoCancelOverdue",
      "path": "/api/v1/reservations/auto-cancel-overdue",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.getLatestAutoCancelRun",
      "module": "RESERVATIONS",
      "label": "Xem lần tự hủy gần nhất",
      "controller": "BookReservationController",
      "handler": "getLatestAutoCancelRun",
      "path": "/api/v1/reservations/auto-cancel-runs/latest",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.getAllAutoCancelRuns",
      "module": "RESERVATIONS",
      "label": "Xem lịch sử tự hủy",
      "controller": "BookReservationController",
      "handler": "getAllAutoCancelRuns",
      "path": "/api/v1/reservations/auto-cancel-runs",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookReservationController.triggerAutoCancelRun",
      "module": "RESERVATIONS",
      "label": "Kích hoạt kiểm tra tự hủy",
      "controller": "BookReservationController",
      "handler": "triggerAutoCancelRun",
      "path": "/api/v1/reservations/auto-cancel-runs/trigger",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookReservationController.getAutoCancelledLast30Days",
      "module": "RESERVATIONS",
      "label": "Xem đơn đã tự hủy trong 30 ngày",
      "controller": "BookReservationController",
      "handler": "getAutoCancelledLast30Days",
      "path": "/api/v1/reservations/auto-cancelled-last-30-days",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "FeePaymentController.search",
      "module": "FEES",
      "label": "Tìm bạn đọc để thu phí",
      "controller": "FeePaymentController",
      "handler": "search",
      "path": "/api/v1/fee-payments/readers",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.",
      "mutating": false
    },
    {
      "id": "FeePaymentController.detail",
      "module": "FEES",
      "label": "Xem công nợ tại quầy thu phí",
      "controller": "FeePaymentController",
      "handler": "detail",
      "path": "/api/v1/fee-payments/readers/{readerId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.",
      "mutating": false
    },
    {
      "id": "FeePaymentController.collect",
      "module": "FEES",
      "label": "Thu toàn bộ phí",
      "controller": "FeePaymentController",
      "handler": "collect",
      "path": "/api/v1/fee-payments/readers/{readerId}/collect-full",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.",
      "mutating": true
    },
    {
      "id": "FeePaymentController.collectPartial",
      "module": "FEES",
      "label": "Thu một phần phí",
      "controller": "FeePaymentController",
      "handler": "collectPartial",
      "path": "/api/v1/fee-payments/readers/{readerId}/collect-partial",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.",
      "mutating": true
    },
    {
      "id": "FeePaymentController.receipt",
      "module": "FEES",
      "label": "Xem và in lại biên lai",
      "controller": "FeePaymentController",
      "handler": "receipt",
      "path": "/api/v1/fee-payments/{paymentId}/receipt",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.",
      "mutating": false
    },
    {
      "id": "FeeReductionController.detail",
      "module": "FEES",
      "label": "Xem chi tiết phí quá hạn",
      "controller": "FeeReductionController",
      "handler": "detail",
      "path": "/api/v1/overdue-fees/{feeId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "FeeReductionController.create",
      "module": "FEES",
      "label": "Lập đề nghị miễn giảm",
      "controller": "FeeReductionController",
      "handler": "create",
      "path": "/api/v1/overdue-fees/{feeId}/reduction-requests",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "FeeReductionController.pending",
      "module": "FEES",
      "label": "Xem đề nghị miễn giảm chờ duyệt",
      "controller": "FeeReductionController",
      "handler": "pending",
      "path": "/api/v1/overdue-fees/reduction-requests/pending",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "FeeReductionController.approve",
      "module": "FEES",
      "label": "Duyệt miễn giảm",
      "controller": "FeeReductionController",
      "handler": "approve",
      "path": "/api/v1/overdue-fees/reduction-requests/{requestId}/approve",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "FeeReductionController.reject",
      "module": "FEES",
      "label": "Từ chối miễn giảm",
      "controller": "FeeReductionController",
      "handler": "reject",
      "path": "/api/v1/overdue-fees/reduction-requests/{requestId}/reject",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookCopyController.repair",
      "module": "STOCK",
      "label": "Chuyển bản sao sang sửa chữa",
      "controller": "BookCopyController",
      "handler": "repair",
      "path": "/api/v1/book-copies/{id}/repair",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookCopyController.history",
      "module": "STOCK",
      "label": "Xem lịch sử trạng thái bản sao",
      "controller": "BookCopyController",
      "handler": "history",
      "path": "/api/v1/book-copies/{id}/status-history",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookCopyController.create",
      "module": "STOCK",
      "label": "Thêm bản sao",
      "controller": "BookCopyController",
      "handler": "create",
      "path": "/api/v1/books/{bookId}/copies",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookCopyController.previewBulk",
      "module": "STOCK",
      "label": "Xem trước mã vạch hàng loạt",
      "controller": "BookCopyController",
      "handler": "previewBulk",
      "path": "/api/v1/books/{bookId}/copies/bulk/preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookCopyController.createBulk",
      "module": "STOCK",
      "label": "Thêm bản sao hàng loạt",
      "controller": "BookCopyController",
      "handler": "createBulk",
      "path": "/api/v1/books/{bookId}/copies/bulk",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookCopyController.getByBookId",
      "module": "STOCK",
      "label": "Xem bản sao của đầu sách",
      "controller": "BookCopyController",
      "handler": "getByBookId",
      "path": "/api/v1/books/{bookId}/copies",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookCopyController.getSummaryByBookId",
      "module": "STOCK",
      "label": "Xem tổng hợp bản sao",
      "controller": "BookCopyController",
      "handler": "getSummaryByBookId",
      "path": "/api/v1/books/{bookId}/copies/summary",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookCopyController.get",
      "module": "STOCK",
      "label": "Xem chi tiết bản sao",
      "controller": "BookCopyController",
      "handler": "get",
      "path": "/api/v1/book-copies/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookCopyController.update",
      "module": "STOCK",
      "label": "Sửa thông tin và vị trí bản sao",
      "controller": "BookCopyController",
      "handler": "update",
      "path": "/api/v1/book-copies/{id}",
      "httpMethods": [
        "PUT",
        "PATCH"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookIncidentController.search",
      "module": "STOCK",
      "label": "Tìm sách để ghi nhận mất/hỏng",
      "controller": "BookIncidentController",
      "handler": "search",
      "path": "/api/v1/book-incidents/open-items",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.preview",
      "module": "STOCK",
      "label": "Xem trước ghi nhận mất/hỏng",
      "controller": "BookIncidentController",
      "handler": "preview",
      "path": "/api/v1/book-incidents/preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.confirm",
      "module": "STOCK",
      "label": "Ghi nhận sách mất/hỏng",
      "controller": "BookIncidentController",
      "handler": "confirm",
      "path": "/api/v1/book-incidents",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookIncidentController.history",
      "module": "STOCK",
      "label": "Xem lịch sử sự cố",
      "controller": "BookIncidentController",
      "handler": "history",
      "path": "/api/v1/book-incidents",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.detail",
      "module": "STOCK",
      "label": "Xem chi tiết sự cố",
      "controller": "BookIncidentController",
      "handler": "detail",
      "path": "/api/v1/book-incidents/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.image",
      "module": "STOCK",
      "label": "Xem ảnh sự cố",
      "controller": "BookIncidentController",
      "handler": "image",
      "path": "/api/v1/book-incidents/{id}/images/{imageId}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.adjustments",
      "module": "STOCK",
      "label": "Xem lịch sử điều chỉnh bồi thường",
      "controller": "BookIncidentController",
      "handler": "adjustments",
      "path": "/api/v1/book-incidents/{id}/adjustments",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.adjustmentPreview",
      "module": "STOCK",
      "label": "Xem trước điều chỉnh bồi thường",
      "controller": "BookIncidentController",
      "handler": "adjustmentPreview",
      "path": "/api/v1/book-incidents/{id}/adjustment-preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "BookIncidentController.requestAdjustment",
      "module": "STOCK",
      "label": "Lập đề nghị điều chỉnh bồi thường",
      "controller": "BookIncidentController",
      "handler": "requestAdjustment",
      "path": "/api/v1/book-incidents/{id}/adjustments",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "BookIncidentController.confirmAdjustment",
      "module": "STOCK",
      "label": "Xác nhận điều chỉnh bồi thường",
      "controller": "BookIncidentController",
      "handler": "confirmAdjustment",
      "path": "/api/v1/book-incidents/{id}/adjustments/{adjustmentId}/confirm",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "OverdueContactController.history",
      "module": "LOANS",
      "label": "Xem lịch sử liên hệ quá hạn",
      "controller": "OverdueContactController",
      "handler": "history",
      "path": "/api/v1/loans/{loanId}/overdue-contacts",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": false
    },
    {
      "id": "OverdueContactController.record",
      "module": "LOANS",
      "label": "Ghi nhận liên hệ quá hạn",
      "controller": "OverdueContactController",
      "handler": "record",
      "path": "/api/v1/loans/{loanId}/overdue-contacts",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.getWarehouses",
      "module": "STOCK",
      "label": "Xem kho để chọn vị trí bản sao",
      "controller": "LibraryConfigurationController",
      "handler": "getWarehouses",
      "path": "/api/v1/library-settings/warehouses",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ rà soát API đọc vị trí phục vụ nghiệp vụ bản sao; các API cấu hình ngoài phạm vi.",
      "mutating": false
    },
    {
      "id": "LibraryConfigurationController.getShelves",
      "module": "STOCK",
      "label": "Xem kệ để chọn vị trí bản sao",
      "controller": "LibraryConfigurationController",
      "handler": "getShelves",
      "path": "/api/v1/library-settings/shelves",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Chỉ rà soát API đọc vị trí phục vụ nghiệp vụ bản sao; các API cấu hình ngoài phạm vi.",
      "mutating": false
    },
    {
      "id": "AuditLogController.search",
      "module": "AUDIT",
      "label": "Tra cứu",
      "controller": "AuditLogController",
      "handler": "search",
      "path": "/api/v1/admin/audit-logs",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuditLogController.filterOptions",
      "module": "AUDIT",
      "label": "Danh sách bộ lọc",
      "controller": "AuditLogController",
      "handler": "filterOptions",
      "path": "/api/v1/admin/audit-logs/filter-options",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuditLogController.getById",
      "module": "AUDIT",
      "label": "Xem chi tiết",
      "controller": "AuditLogController",
      "handler": "getById",
      "path": "/api/v1/admin/audit-logs/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuditLogController.rejectPut",
      "module": "AUDIT",
      "label": "Chặn ghi đè nhật ký",
      "controller": "AuditLogController",
      "handler": "rejectPut",
      "path": "/api/v1/admin/audit-logs/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "IMMUTABLE",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu.",
      "mutating": true
    },
    {
      "id": "AuditLogController.rejectPatch",
      "module": "AUDIT",
      "label": "Chặn sửa nhật ký",
      "controller": "AuditLogController",
      "handler": "rejectPatch",
      "path": "/api/v1/admin/audit-logs/{id}",
      "httpMethods": [
        "PATCH"
      ],
      "rules": {
        "ADMIN": "IMMUTABLE",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu.",
      "mutating": true
    },
    {
      "id": "AuditLogController.rejectDelete",
      "module": "AUDIT",
      "label": "Chặn xóa nhật ký",
      "controller": "AuditLogController",
      "handler": "rejectDelete",
      "path": "/api/v1/admin/audit-logs/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "IMMUTABLE",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu.",
      "mutating": true
    },
    {
      "id": "AuthController.login",
      "module": "AUTH",
      "label": "Đăng nhập",
      "controller": "AuthController",
      "handler": "login",
      "path": "/api/v1/auth/login",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "AuthController.refresh",
      "module": "AUTH",
      "label": "Làm mới phiên",
      "controller": "AuthController",
      "handler": "refresh",
      "path": "/api/v1/auth/refresh",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "AuthController.me",
      "module": "AUTH",
      "label": "Thông tin tài khoản đang đăng nhập",
      "controller": "AuthController",
      "handler": "me",
      "path": "/api/v1/auth/me",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "OWN",
        "LIBRARY_MANAGER": "OWN",
        "LIBRARIAN": "OWN",
        "READER": "OWN"
      },
      "condition": "Luôn lấy tài khoản từ JWT; không nhận userId do khách gửi.",
      "mutating": false
    },
    {
      "id": "AuthorController.getAllAuthors",
      "module": "CATALOG",
      "label": "Danh sách tác giả",
      "controller": "AuthorController",
      "handler": "getAllAuthors",
      "path": "/api/v1/authors",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuthorController.getActiveAuthors",
      "module": "CATALOG",
      "label": "Tác giả đang sử dụng",
      "controller": "AuthorController",
      "handler": "getActiveAuthors",
      "path": "/api/v1/authors/active",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuthorController.getAuthorById",
      "module": "CATALOG",
      "label": "Chi tiết tác giả",
      "controller": "AuthorController",
      "handler": "getAuthorById",
      "path": "/api/v1/authors/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "AuthorController.createAuthor",
      "module": "CATALOG",
      "label": "Thêm tác giả",
      "controller": "AuthorController",
      "handler": "createAuthor",
      "path": "/api/v1/authors",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "AuthorController.updateAuthor",
      "module": "CATALOG",
      "label": "Sửa tác giả",
      "controller": "AuthorController",
      "handler": "updateAuthor",
      "path": "/api/v1/authors/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "AuthorController.toggleStatus",
      "module": "CATALOG",
      "label": "Đổi trạng thái",
      "controller": "AuthorController",
      "handler": "toggleStatus",
      "path": "/api/v1/authors/{id}/toggle-status",
      "httpMethods": [
        "PATCH"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "AuthorController.deleteAuthor",
      "module": "CATALOG",
      "label": "Xóa tác giả",
      "controller": "AuthorController",
      "handler": "deleteAuthor",
      "path": "/api/v1/authors/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "BookCatalogController.getAllBooks",
      "module": "CATALOG",
      "label": "Danh sách đầu sách nội bộ",
      "controller": "BookCatalogController",
      "handler": "getAllBooks",
      "path": "/api/v1/books",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getPublicBooks",
      "module": "PUBLIC",
      "label": "Danh sách sách công khai",
      "controller": "BookCatalogController",
      "handler": "getPublicBooks",
      "path": "/api/v1/books/public",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.searchPublicBooks",
      "module": "PUBLIC",
      "label": "Tìm sách công khai",
      "controller": "BookCatalogController",
      "handler": "searchPublicBooks",
      "path": "/api/v1/books/public/search",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getMostBorrowedAvailableBooks",
      "module": "PUBLIC",
      "label": "Sách nổi bật",
      "controller": "BookCatalogController",
      "handler": "getMostBorrowedAvailableBooks",
      "path": "/api/v1/books/public/featured",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getPublicFilterOptions",
      "module": "PUBLIC",
      "label": "Bộ lọc sách công khai",
      "controller": "BookCatalogController",
      "handler": "getPublicFilterOptions",
      "path": "/api/v1/books/public/filters",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getPublicBookById",
      "module": "PUBLIC",
      "label": "Chi tiết sách công khai",
      "controller": "BookCatalogController",
      "handler": "getPublicBookById",
      "path": "/api/v1/books/public/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getAvailableCopiesByBookId",
      "module": "PUBLIC",
      "label": "Bản sao có thể mượn công khai",
      "controller": "BookCatalogController",
      "handler": "getAvailableCopiesByBookId",
      "path": "/api/v1/books/public/{id}/available-copies",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getPublicBookQueue",
      "module": "PUBLIC",
      "label": "Hàng chờ công khai",
      "controller": "BookCatalogController",
      "handler": "getPublicBookQueue",
      "path": "/api/v1/books/public/{id}/queue",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getPublisherOptions",
      "module": "CATALOG",
      "label": "Danh sách nhà xuất bản",
      "controller": "BookCatalogController",
      "handler": "getPublisherOptions",
      "path": "/api/v1/books/publishers",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.getById",
      "module": "CATALOG",
      "label": "Xem chi tiết",
      "controller": "BookCatalogController",
      "handler": "getById",
      "path": "/api/v1/books/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "BookCatalogController.catalogBook",
      "module": "CATALOG",
      "label": "Biên mục đầu sách",
      "controller": "BookCatalogController",
      "handler": "catalogBook",
      "path": "/api/v1/books",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "BookCoverController.upload",
      "module": "CATALOG",
      "label": "Tải ảnh bìa",
      "controller": "BookCoverController",
      "handler": "upload",
      "path": "/api/v1/books/{id}/cover",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "BookCoverController.get",
      "module": "CATALOG",
      "label": "Ảnh bìa nội bộ",
      "controller": "BookCoverController",
      "handler": "get",
      "path": "/api/v1/books/{id}/cover",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "BookCoverController.getPublic",
      "module": "PUBLIC",
      "label": "Ảnh bìa công khai",
      "controller": "BookCoverController",
      "handler": "getPublic",
      "path": "/api/v1/books/public/{id}/cover",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookCoverController.getPublicThumbnail",
      "module": "PUBLIC",
      "label": "Ảnh bìa thu nhỏ công khai",
      "controller": "BookCoverController",
      "handler": "getPublicThumbnail",
      "path": "/api/v1/books/public/{id}/cover/thumbnail",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "BookIncidentController.factors",
      "module": "CONFIG",
      "label": "Xem hệ số bồi thường",
      "controller": "BookIncidentController",
      "handler": "factors",
      "path": "/api/v1/book-incidents/factors",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "BookIncidentController.update",
      "module": "CONFIG",
      "label": "Cập nhật hệ số bồi thường",
      "controller": "BookIncidentController",
      "handler": "update",
      "path": "/api/v1/book-incidents/factors",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CardTypeController.getAllCardTypes",
      "module": "CONFIG",
      "label": "Danh sách loại thẻ",
      "controller": "CardTypeController",
      "handler": "getAllCardTypes",
      "path": "/api/v1/card-types",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CardTypeController.getActiveCardTypes",
      "module": "CONFIG",
      "label": "Loại thẻ đang sử dụng",
      "controller": "CardTypeController",
      "handler": "getActiveCardTypes",
      "path": "/api/v1/card-types/active",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CardTypeController.getCardTypeById",
      "module": "CONFIG",
      "label": "Chi tiết loại thẻ",
      "controller": "CardTypeController",
      "handler": "getCardTypeById",
      "path": "/api/v1/card-types/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CardTypeController.getPolicyHistory",
      "module": "CONFIG",
      "label": "Lịch sử chính sách mượn",
      "controller": "CardTypeController",
      "handler": "getPolicyHistory",
      "path": "/api/v1/card-types/history",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CardTypeController.createCardType",
      "module": "CONFIG",
      "label": "Thêm loại thẻ",
      "controller": "CardTypeController",
      "handler": "createCardType",
      "path": "/api/v1/card-types",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CardTypeController.updateCardType",
      "module": "CONFIG",
      "label": "Sửa loại thẻ",
      "controller": "CardTypeController",
      "handler": "updateCardType",
      "path": "/api/v1/card-types/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CardTypeController.toggleStatus",
      "module": "CONFIG",
      "label": "Đổi trạng thái",
      "controller": "CardTypeController",
      "handler": "toggleStatus",
      "path": "/api/v1/card-types/{id}/toggle-status",
      "httpMethods": [
        "PATCH"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CardTypeController.deleteCardType",
      "module": "CONFIG",
      "label": "Xóa loại thẻ",
      "controller": "CardTypeController",
      "handler": "deleteCardType",
      "path": "/api/v1/card-types/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CategoryController.getAllCategories",
      "module": "CATALOG",
      "label": "Danh sách thể loại",
      "controller": "CategoryController",
      "handler": "getAllCategories",
      "path": "/api/v1/categories",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CategoryController.getActiveCategories",
      "module": "CATALOG",
      "label": "Thể loại đang sử dụng",
      "controller": "CategoryController",
      "handler": "getActiveCategories",
      "path": "/api/v1/categories/active",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CategoryController.getCategoryById",
      "module": "CATALOG",
      "label": "Chi tiết thể loại",
      "controller": "CategoryController",
      "handler": "getCategoryById",
      "path": "/api/v1/categories/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "CategoryController.createCategory",
      "module": "CATALOG",
      "label": "Thêm thể loại",
      "controller": "CategoryController",
      "handler": "createCategory",
      "path": "/api/v1/categories",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CategoryController.updateCategory",
      "module": "CATALOG",
      "label": "Sửa thể loại",
      "controller": "CategoryController",
      "handler": "updateCategory",
      "path": "/api/v1/categories/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CategoryController.toggleStatus",
      "module": "CATALOG",
      "label": "Đổi trạng thái",
      "controller": "CategoryController",
      "handler": "toggleStatus",
      "path": "/api/v1/categories/{id}/toggle-status",
      "httpMethods": [
        "PATCH"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "CategoryController.deleteCategory",
      "module": "CATALOG",
      "label": "Xóa thể loại",
      "controller": "CategoryController",
      "handler": "deleteCategory",
      "path": "/api/v1/categories/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "DashboardController.getStats",
      "module": "REPORTS",
      "label": "Thống kê nội bộ",
      "controller": "DashboardController",
      "handler": "getStats",
      "path": "/api/v1/dashboard/stats",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "InitialPasswordController.validateToken",
      "module": "AUTH",
      "label": "Kiểm tra liên kết mật khẩu ban đầu",
      "controller": "InitialPasswordController",
      "handler": "validateToken",
      "path": "/api/v1/auth/initial-password",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "InitialPasswordController.setInitialPassword",
      "module": "AUTH",
      "label": "Thiết lập mật khẩu ban đầu",
      "controller": "InitialPasswordController",
      "handler": "setInitialPassword",
      "path": "/api/v1/auth/initial-password",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "LibraryCardController.suggestionSettings",
      "module": "READERS",
      "label": "Cấu hình đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "suggestionSettings",
      "path": "/api/v1/library-cards/lock-suggestions/settings",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LibraryConfigurationController.getOverdueFee",
      "module": "CONFIG",
      "label": "Xem cấu hình phí quá hạn",
      "controller": "LibraryConfigurationController",
      "handler": "getOverdueFee",
      "path": "/api/v1/library-settings/overdue-fee",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LibraryConfigurationController.updateOverdueFee",
      "module": "CONFIG",
      "label": "Cập nhật cấu hình phí quá hạn",
      "controller": "LibraryConfigurationController",
      "handler": "updateOverdueFee",
      "path": "/api/v1/library-settings/overdue-fee",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.createWarehouse",
      "module": "CONFIG",
      "label": "Thêm kho",
      "controller": "LibraryConfigurationController",
      "handler": "createWarehouse",
      "path": "/api/v1/library-settings/warehouses",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.updateWarehouse",
      "module": "CONFIG",
      "label": "Sửa kho",
      "controller": "LibraryConfigurationController",
      "handler": "updateWarehouse",
      "path": "/api/v1/library-settings/warehouses/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.createShelf",
      "module": "CONFIG",
      "label": "Thêm kệ",
      "controller": "LibraryConfigurationController",
      "handler": "createShelf",
      "path": "/api/v1/library-settings/shelves",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.updateShelf",
      "module": "CONFIG",
      "label": "Sửa kệ",
      "controller": "LibraryConfigurationController",
      "handler": "updateShelf",
      "path": "/api/v1/library-settings/shelves/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.deleteShelf",
      "module": "CONFIG",
      "label": "Xóa kệ",
      "controller": "LibraryConfigurationController",
      "handler": "deleteShelf",
      "path": "/api/v1/library-settings/shelves/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.getWeeklySchedule",
      "module": "CONFIG",
      "label": "Xem lịch làm việc",
      "controller": "LibraryConfigurationController",
      "handler": "getWeeklySchedule",
      "path": "/api/v1/library-settings/weekly-schedule",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LibraryConfigurationController.updateWeeklySchedule",
      "module": "CONFIG",
      "label": "Cập nhật lịch làm việc",
      "controller": "LibraryConfigurationController",
      "handler": "updateWeeklySchedule",
      "path": "/api/v1/library-settings/weekly-schedule",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.getClosedDates",
      "module": "CONFIG",
      "label": "Xem ngày nghỉ",
      "controller": "LibraryConfigurationController",
      "handler": "getClosedDates",
      "path": "/api/v1/library-settings/closed-dates",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LibraryConfigurationController.createClosedDate",
      "module": "CONFIG",
      "label": "Thêm ngày nghỉ",
      "controller": "LibraryConfigurationController",
      "handler": "createClosedDate",
      "path": "/api/v1/library-settings/closed-dates",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.createClosedDatesBulk",
      "module": "CONFIG",
      "label": "Thêm nhiều ngày nghỉ",
      "controller": "LibraryConfigurationController",
      "handler": "createClosedDatesBulk",
      "path": "/api/v1/library-settings/closed-dates/bulk",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.updateClosedDate",
      "module": "CONFIG",
      "label": "Sửa ngày nghỉ",
      "controller": "LibraryConfigurationController",
      "handler": "updateClosedDate",
      "path": "/api/v1/library-settings/closed-dates/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.deleteClosedDate",
      "module": "CONFIG",
      "label": "Xóa ngày nghỉ",
      "controller": "LibraryConfigurationController",
      "handler": "deleteClosedDate",
      "path": "/api/v1/library-settings/closed-dates/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LibraryConfigurationController.adjustDueDate",
      "module": "CONFIG",
      "label": "Xem ngày trả sau điều chỉnh",
      "controller": "LibraryConfigurationController",
      "handler": "adjustDueDate",
      "path": "/api/v1/library-settings/adjust-due-date",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LibraryPublisherController.create",
      "module": "CATALOG",
      "label": "Thêm nhà xuất bản",
      "controller": "LibraryPublisherController",
      "handler": "create",
      "path": "/api/v1/publishers",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LoanReminderController.previewReminders",
      "module": "EMAIL",
      "label": "Xem trước email nhắc trả",
      "controller": "LoanReminderController",
      "handler": "previewReminders",
      "path": "/api/v1/loan-reminders/preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LoanReminderController.triggerReminders",
      "module": "EMAIL",
      "label": "Chạy gửi email nhắc trả",
      "controller": "LoanReminderController",
      "handler": "triggerReminders",
      "path": "/api/v1/loan-reminders/trigger",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LoanReminderController.retryPending",
      "module": "EMAIL",
      "label": "Gửi lại email đang chờ",
      "controller": "LoanReminderController",
      "handler": "retryPending",
      "path": "/api/v1/loan-reminders/retry-pending",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LoanReminderController.retrySingle",
      "module": "EMAIL",
      "label": "Gửi lại một email",
      "controller": "LoanReminderController",
      "handler": "retrySingle",
      "path": "/api/v1/loan-reminders/{id}/retry",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "LoanReminderController.getHistory",
      "module": "EMAIL",
      "label": "Lịch sử gửi email",
      "controller": "LoanReminderController",
      "handler": "getHistory",
      "path": "/api/v1/loan-reminders/history",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "LoanReportController.get",
      "module": "REPORTS",
      "label": "Báo cáo mượn theo ngày và hàng chờ",
      "controller": "LoanReportController",
      "handler": "get",
      "path": "/api/v1/reports/loans",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "OverdueDebtReportController.preview",
      "module": "REPORTS",
      "label": "Xem báo cáo nợ quá hạn",
      "controller": "OverdueDebtReportController",
      "handler": "preview",
      "path": "/api/v1/reports/overdue-debts",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "OverdueDebtReportController.export",
      "module": "REPORTS",
      "label": "Xuất báo cáo nợ quá hạn",
      "controller": "OverdueDebtReportController",
      "handler": "export",
      "path": "/api/v1/reports/overdue-debts/export",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "OverdueDebtReportController.cardTypes",
      "module": "REPORTS",
      "label": "Bộ lọc loại thẻ báo cáo",
      "controller": "OverdueDebtReportController",
      "handler": "cardTypes",
      "path": "/api/v1/reports/overdue-debts/filter-options",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "OverdueDebtReportController.history",
      "module": "REPORTS",
      "label": "Xem lịch sử",
      "controller": "OverdueDebtReportController",
      "handler": "history",
      "path": "/api/v1/reports/overdue-debts/history",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "PasswordResetController.forgotPassword",
      "module": "AUTH",
      "label": "Yêu cầu đặt lại mật khẩu",
      "controller": "PasswordResetController",
      "handler": "forgotPassword",
      "path": "/api/v1/auth/forgot-password",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "PasswordResetController.validateResetPasswordToken",
      "module": "AUTH",
      "label": "Kiểm tra liên kết đặt lại mật khẩu",
      "controller": "PasswordResetController",
      "handler": "validateResetPasswordToken",
      "path": "/api/v1/auth/reset-password/validate",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "PasswordResetController.resetPassword",
      "module": "AUTH",
      "label": "Đặt lại mật khẩu",
      "controller": "PasswordResetController",
      "handler": "resetPassword",
      "path": "/api/v1/auth/reset-password",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "PermissionReviewController.inspect",
      "module": "REVIEW",
      "label": "Đối chiếu ma trận quyền",
      "controller": "PermissionReviewController",
      "handler": "inspect",
      "path": "/api/v1/permission-review",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.reports",
      "module": "REVIEW",
      "label": "Danh sách biên bản rà soát",
      "controller": "PermissionReviewController",
      "handler": "reports",
      "path": "/api/v1/permission-review/reports",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.detail",
      "module": "REVIEW",
      "label": "Chi tiết biên bản rà soát",
      "controller": "PermissionReviewController",
      "handler": "detail",
      "path": "/api/v1/permission-review/reports/{id}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.importReport",
      "module": "REVIEW",
      "label": "Nhập biên bản kiểm thử",
      "controller": "PermissionReviewController",
      "handler": "importReport",
      "path": "/api/v1/permission-review/reports",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "PickupLastDayReminderController.preview",
      "module": "EMAIL",
      "label": "Xem trước nhắc ngày cuối nhận sách",
      "controller": "PickupLastDayReminderController",
      "handler": "preview",
      "path": "/api/v1/reservations/pickup-last-day-reminders/preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "PickupLastDayReminderController.run",
      "module": "EMAIL",
      "label": "Chạy nhắc ngày cuối nhận sách",
      "controller": "PickupLastDayReminderController",
      "handler": "run",
      "path": "/api/v1/reservations/pickup-last-day-reminders/run",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "PublicLibraryOverviewController.getOverview",
      "module": "PUBLIC",
      "label": "Tổng quan công khai",
      "controller": "PublicLibraryOverviewController",
      "handler": "getOverview",
      "path": "/api/v1/public/overview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "ReaderController.checkDuplicate",
      "module": "PUBLIC",
      "label": "Kiểm tra trùng đăng ký",
      "controller": "ReaderController",
      "handler": "checkDuplicate",
      "path": "/api/v1/readers/check-duplicate",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": false
    },
    {
      "id": "ReaderController.register",
      "module": "PUBLIC",
      "label": "Đăng ký bạn đọc",
      "controller": "ReaderController",
      "handler": "register",
      "path": "/api/v1/readers/register",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "PUBLIC",
        "LIBRARY_MANAGER": "PUBLIC",
        "LIBRARIAN": "PUBLIC",
        "READER": "PUBLIC"
      },
      "condition": "Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng.",
      "mutating": true
    },
    {
      "id": "ReadyPickupNotificationController.history",
      "module": "EMAIL",
      "label": "Xem lịch sử",
      "controller": "ReadyPickupNotificationController",
      "handler": "history",
      "path": "/api/v1/reservations/{reservationId}/ready-email",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "ReadyPickupNotificationController.previewCancellation",
      "module": "EMAIL",
      "label": "Xem trước hủy sau email nhận sách",
      "controller": "ReadyPickupNotificationController",
      "handler": "previewCancellation",
      "path": "/api/v1/reservations/{reservationId}/ready-email/cancellation-preview",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "ReadyPickupNotificationController.runCancellation",
      "module": "EMAIL",
      "label": "Hủy đặt giữ theo điều kiện email",
      "controller": "ReadyPickupNotificationController",
      "handler": "runCancellation",
      "path": "/api/v1/reservations/{reservationId}/ready-email/cancellation-run",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "ReadyPickupNotificationController.trigger",
      "module": "EMAIL",
      "label": "Gửi email sách sẵn sàng",
      "controller": "ReadyPickupNotificationController",
      "handler": "trigger",
      "path": "/api/v1/reservations/{reservationId}/ready-email/trigger",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "UserManagementController.createAccount",
      "module": "ACCOUNTS",
      "label": "Tạo tài khoản nhân sự",
      "controller": "UserManagementController",
      "handler": "createAccount",
      "path": "/api/v1/admin/accounts",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "UserManagementController.getAccounts",
      "module": "ACCOUNTS",
      "label": "Danh sách tài khoản nhân sự",
      "controller": "UserManagementController",
      "handler": "getAccounts",
      "path": "/api/v1/admin/accounts",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "ALLOW",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": false
    },
    {
      "id": "UserManagementController.updateAccount",
      "module": "ACCOUNTS",
      "label": "Cập nhật tài khoản nhân sự",
      "controller": "UserManagementController",
      "handler": "updateAccount",
      "path": "/api/v1/admin/accounts/{id}",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "UserManagementController.deleteAccount",
      "module": "ACCOUNTS",
      "label": "Xóa tài khoản nhân sự",
      "controller": "UserManagementController",
      "handler": "deleteAccount",
      "path": "/api/v1/admin/accounts/{id}",
      "httpMethods": [
        "DELETE"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "UserManagementController.updateAccountStatus",
      "module": "ACCOUNTS",
      "label": "Khóa hoặc mở tài khoản nhân sự",
      "controller": "UserManagementController",
      "handler": "updateAccountStatus",
      "path": "/api/v1/admin/accounts/{id}/status",
      "httpMethods": [
        "PATCH"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có.",
      "mutating": true
    },
    {
      "id": "PermissionReviewController.startRun",
      "module": "REVIEW",
      "label": "Chạy kiểm thử toàn bộ",
      "controller": "PermissionReviewController",
      "handler": "startRun",
      "path": "/api/v1/permission-review/runs",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": true
    },
    {
      "id": "PermissionReviewController.latestRun",
      "module": "REVIEW",
      "label": "Lần kiểm thử gần nhất",
      "controller": "PermissionReviewController",
      "handler": "latestRun",
      "path": "/api/v1/permission-review/runs/latest",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.runStatus",
      "module": "REVIEW",
      "label": "Tiến độ lần kiểm thử",
      "controller": "PermissionReviewController",
      "handler": "runStatus",
      "path": "/api/v1/permission-review/runs/{runKey}",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.coverage",
      "module": "REVIEW",
      "label": "Độ bao phủ và điều kiện hoàn tất",
      "controller": "PermissionReviewController",
      "handler": "coverage",
      "path": "/api/v1/permission-review/reports/{id}/coverage",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": false
    },
    {
      "id": "PermissionReviewController.complete",
      "module": "REVIEW",
      "label": "Xác nhận hoàn tất rà soát",
      "controller": "PermissionReviewController",
      "handler": "complete",
      "path": "/api/v1/permission-review/reports/{id}/complete",
      "httpMethods": [
        "POST"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": true
    },
    {
      "id": "PermissionReviewController.export",
      "module": "REVIEW",
      "label": "Xuất biên bản đầy đủ",
      "controller": "PermissionReviewController",
      "handler": "export",
      "path": "/api/v1/permission-review/reports/{id}/export",
      "httpMethods": [
        "GET"
      ],
      "rules": {
        "ADMIN": "ALLOW",
        "LIBRARY_MANAGER": "DENY",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống.",
      "mutating": false
    },
    {
      "id": "LibraryCardController.updateSuggestionSettings",
      "module": "READERS",
      "label": "Cập nhật ngưỡng đề xuất khóa thẻ",
      "controller": "LibraryCardController",
      "handler": "updateSuggestionSettings",
      "path": "/api/v1/library-cards/lock-suggestions/settings",
      "httpMethods": [
        "PUT"
      ],
      "rules": {
        "ADMIN": "DENY",
        "LIBRARY_MANAGER": "ALLOW",
        "LIBRARIAN": "DENY",
        "READER": "DENY"
      },
      "condition": "Giữ quyền Quản lý thư viện duy nhất của cấu hình đề xuất khóa thẻ theo source và nguyên tắc tách nhiệm vụ đã chốt.",
      "mutating": true
    }
  ]
}
````

## backend/src/test/java/com/duanttcsn5/library/CriticalPermissionApiTest.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.*;
import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.*;
import jakarta.validation.constraints.Null;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real Spring Security + real controllers/validation/JWT filter, isolated mocked business services.
 * This suite explicitly does NOT claim database side effects have been verified.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CriticalPermissionApiTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class, GlobalExceptionHandler.class,
            PermissionReviewInspector.class, ReaderController.class, LibraryCardController.class,
            LoanController.class, BookReservationController.class, FeePaymentController.class,
            FeeReductionController.class, BookCopyController.class, BookIncidentController.class,
            OverdueContactController.class, LibraryConfigurationController.class})
    static class Config {
        @Bean PermissionReviewCatalog catalog(ObjectMapper mapper) throws Exception { return new PermissionReviewCatalog(mapper, "permissions/S4-09.1-policy.json"); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean JwtService jwt() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ReaderRegistrationService readerRegistration() { return business(ReaderRegistrationService.class); }
        @Bean ReaderSelfService readerSelf() { return business(ReaderSelfService.class); }
        @Bean ReaderRegistrationRateLimitService registrationLimit() { return business(ReaderRegistrationRateLimitService.class); }
        @Bean LibraryCardService cards() { return business(LibraryCardService.class); }
        @Bean CardLockSuggestionService suggestions() { return business(CardLockSuggestionService.class); }
        @Bean LoanService loans() { return business(LoanService.class); }
        @Bean LoanRejectionLogService rejections() { return business(LoanRejectionLogService.class); }
        @Bean BookReservationService reservations() { return business(BookReservationService.class); }
        @Bean ReservationAutoCancellationService autoCancellation() { return business(ReservationAutoCancellationService.class); }
        @Bean FeePaymentService payments() { return business(FeePaymentService.class); }
        @Bean FeeReductionService reductions() { return business(FeeReductionService.class); }
        @Bean BookCopyService copies() { return business(BookCopyService.class); }
        @Bean BookIncidentService incidents() { return business(BookIncidentService.class); }
        @Bean OverdueContactService contacts() { return business(OverdueContactService.class); }
        @Bean LibraryConfigurationService settings() { return business(LibraryConfigurationService.class); }
        @Bean ReadyPickupNotificationService notifications() { return business(ReadyPickupNotificationService.class); }
    }
    static <T> T business(Class<T> type) {
        return mock(type, invocation -> {
            Class<?> result = invocation.getMethod().getReturnType();
            return result.isRecord() ? sample(result, "response", false) : Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    PermissionReviewCatalog catalog;
    PermissionReviewInspector inspector;
    ObjectMapper mapper;
    List<Object> services;
    OffsetDateTime started;
    List<EvidenceCase> evidence = new ArrayList<>();

    @BeforeAll void setup() {
        started = OffsetDateTime.now();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        catalog = context.getBean(PermissionReviewCatalog.class);
        inspector = context.getBean(PermissionReviewInspector.class); mapper = context.getBean(ObjectMapper.class);
        services = Stream.of(ReaderRegistrationService.class, ReaderSelfService.class,
                ReaderRegistrationRateLimitService.class, LibraryCardService.class, CardLockSuggestionService.class,
                LoanService.class, LoanRejectionLogService.class, BookReservationService.class,
                ReservationAutoCancellationService.class, FeePaymentService.class, FeeReductionService.class,
                BookCopyService.class, BookIncidentService.class, OverdueContactService.class,
                LibraryConfigurationService.class, ReadyPickupNotificationService.class).map(context::getBean)
                .map(value -> (Object) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(value)).toList();
        for (String code : List.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER")) {
            long id = 100 + catalog.policy().roles().stream().map(RoleOption::code).toList().indexOf(code);
            Role role = new Role(); role.setCode(code);
            User user = new User(); user.setId(id); user.setRole(role); user.setStatus("ACTIVE"); user.setFullName("Kiểm thử " + code);
            user.setTokenVersion(0);
            when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(user));
            when(context.getBean(JwtService.class).decode("review-" + code)).thenReturn(Jwt.withTokenValue("review-" + code)
                    .header("alg", "HS256").subject(Long.toString(id)).claim("tokenVersion", 0).build());
        }
    }

    @Test void documentHasFourRolesAndAllEightyFourEndpointsAreMappedAndCompared() {
        var inspection = inspector.inspect();
        assertEquals(84, inspection.reviewedFunctions());
        assertEquals(0, inspection.unresolvedFunctions());
        assertEquals(0, inspection.differentFunctions(), () -> inspection.entries().stream()
                .filter(ReviewedEntry::different).map(value -> value.policy().id()).toList().toString());
        assertEquals(Set.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER"), catalog.policy().entries().get(0).rules().keySet());
    }

    @TestFactory Stream<DynamicTest> allPriorityApisWithEveryRoleAndAnonymous() {
        return catalog.policy().entries().stream().flatMap(entry ->
                Stream.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER", "ANONYMOUS")
                        .map(role -> DynamicTest.dynamicTest(entry.id() + " / " + role, () -> probe(entry, role, "STANDARD"))));
    }

    @TestFactory Stream<DynamicTest> managersMayUseOnlyTheExistingExplicitOverrideFlow() {
        return catalog.policy().entries().stream().filter(entry -> "OVERRIDE".equals(entry.rules().get("LIBRARY_MANAGER")))
                .map(entry -> DynamicTest.dynamicTest(entry.id() + " / manager override", () -> probe(entry, "LIBRARY_MANAGER", "OVERRIDE")));
    }

    void probe(PolicyEntry entry, String role, String scenario) throws Exception {
        int success = successStatus(entry);
        int expected = catalog.requiredStatus(entry, role, scenario, success);
        int actual = 0;
        boolean unchanged = true;
        OffsetDateTime detected = OffsetDateTime.now();
        StringBuilder diagnostic = new StringBuilder();
        for (String verb : entry.httpMethods()) {
            for (Object service : services) clearInvocations(service);
            var result = mvc.perform(request(entry, verb, role, scenario)).andReturn();
            int status = result.getResponse().getStatus();
            if (actual == 0 || status != expected) actual = status;
            boolean noBusinessCalls = services.stream().allMatch(service -> mockingDetails(service).getInvocations().isEmpty());
            if (expected == 401 || expected == 403) unchanged &= noBusinessCalls;
            diagnostic.append(verb).append(':').append(status).append("; ");
        }
        Boolean mutationCheck = (expected == 401 || expected == 403) ? unchanged : null;
        evidence.add(new EvidenceCase(entry.id(), role, scenario, expected, null, actual, mutationCheck,
                detected, OffsetDateTime.now(), "MockMvc: cổng bảo mật, controller và validation thật; JWT decoder và service nghiệp vụ giả lập. "
                + diagnostic + ((expected == 401 || expected == 403) ? "Không có lời gọi service nghiệp vụ: " + unchanged : "Kết quả nghiệp vụ dùng dữ liệu giả lập, không kiểm chứng PostgreSQL."), null));
        assertEquals(expected, actual, diagnostic + " body/handler=" + entry.handler());
        if (expected == 401 || expected == 403) assertTrue(unchanged, "Yêu cầu bị chặn không được gọi service nghiệp vụ.");
    }

    static int successStatus(PolicyEntry entry) {
        if (entry.id().equals("BookReservationController.cancelMine")) return 204;
        if (entry.id().equals("LibraryCardController.reject")) return 204;
        if (entry.id().equals("BookReservationController.getLatestAutoCancelRun")) return 204;
        Set<String> created = Set.of("LibraryCardController.approve", "LoanController.createFromReservation",
                "LoanController.createFromReservations", "LoanController.createDirectLoan", "BookReservationController.reserve",
                "BookReservationController.reserveMany", "FeeReductionController.create", "BookCopyController.create",
                "BookCopyController.createBulk", "BookIncidentController.confirm", "BookIncidentController.requestAdjustment",
                "OverdueContactController.record");
        return created.contains(entry.id()) ? 201 : 200;
    }

    AbstractMockHttpServletRequestBuilder<?> request(PolicyEntry entry, String verb, String role, String scenario) throws Exception {
        String path = entry.path().replaceAll("\\{[^}]+}", "1");
        Class<?> controller = Class.forName("com.duanttcsn5.library.controller." + entry.controller());
        Method method = Arrays.stream(controller.getDeclaredMethods()).filter(value -> value.getName().equals(entry.handler())).findFirst().orElseThrow();
        boolean multipart = method.isAnnotationPresent(PostMapping.class)
                && Arrays.stream(method.getAnnotation(PostMapping.class).consumes()).anyMatch(value -> value.contains("multipart"));
        AbstractMockHttpServletRequestBuilder<?> request = multipart ? multipart(path)
                : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(verb), path);
        if (!role.equals("ANONYMOUS")) request.header("Authorization", "Bearer review-" + role);
        for (Parameter parameter : method.getParameters()) {
            RequestParam query = parameter.getAnnotation(RequestParam.class);
            if (query != null && query.required() && query.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
                String name = !query.value().isEmpty() ? query.value() : !query.name().isEmpty() ? query.name() : parameter.getName();
                if (!List.class.isAssignableFrom(parameter.getType())) request.param(name, Objects.toString(sample(parameter.getType(), name, true)));
            }
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                Object body = sample(parameter.getType(), "request", true);
                if (body instanceof Map<?, ?> value && scenario.equals("OVERRIDE")) applyOverride(value);
                request.contentType("application/json").content(mapper.writeValueAsString(body));
            }
        }
        return request;
    }
    @SuppressWarnings("unchecked") static void applyOverride(Map<?, ?> raw) {
        Map<String, Object> body = (Map<String, Object>) raw;
        body.put("overrideRequested", true); body.put("overridePreview", true); body.put("overrideReason", "Ngoại lệ kiểm thử đã được phê duyệt.");
        if (body.get("confirmation") instanceof Map<?, ?> confirmation) applyOverride(confirmation);
    }

    static Object sample(Type type, String name, boolean json) throws Exception {
        if (type instanceof ParameterizedType generic) {
            if (generic.getRawType() == List.class) return List.of(sample(generic.getActualTypeArguments()[0], name, json));
            if (generic.getRawType() == Map.class) return Map.of();
            if (generic.getRawType() == Optional.class) return Optional.empty();
            type = generic.getRawType();
        }
        Class<?> cls = (Class<?>) type;
        if (cls == String.class) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("email")) return "review@example.invalid";
            if (lower.contains("password")) return "ReviewTest123!";
            if (lower.contains("phone")) return "0912345678";
            if (lower.contains("token")) return "a".repeat(64);
            if (lower.contains("barcode")) return "REVIEW-001";
            if (lower.contains("cardnumber")) return "REVIEW-CARD-001";
            if (lower.contains("filename")) return "review.csv";
            if (lower.contains("contenttype")) return "image/png";
            return "Kiểm thử S4-09.1";
        }
        if (cls == long.class || cls == Long.class) return 1L;
        if (cls == int.class || cls == Integer.class) return 1;
        if (cls == boolean.class || cls == Boolean.class) return false;
        if (cls == BigDecimal.class) return BigDecimal.ONE;
        if (cls == UUID.class) return UUID.fromString("4b40f1ca-5cb8-47d7-b293-eeb16f63466a");
        if (cls == LocalDate.class) return LocalDate.now().plusYears(1);
        if (cls == OffsetDateTime.class) return OffsetDateTime.now().plusDays(1);
        if (cls == byte[].class) return new byte[]{1, 2, 3};
        if (cls.isEnum()) return cls.getSimpleName().equals("BarcodeMode")
                ? Arrays.stream(cls.getEnumConstants()).filter(value -> value.toString().equals("MANUAL")).findFirst().orElseThrow()
                : cls.getEnumConstants()[0];
        if (cls == List.class) return List.of();
        if (cls == Map.class) return Map.of();
        if (!cls.isRecord()) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        List<Object> arguments = new ArrayList<>();
        List<Class<?>> types = new ArrayList<>();
        for (RecordComponent component : cls.getRecordComponents()) {
            Object value = cls.getDeclaredField(component.getName()).isAnnotationPresent(Null.class)
                    ? null : sample(component.getGenericType(), component.getName(), json);
            values.put(component.getName(), value); arguments.add(value); types.add(component.getType());
        }
        if (json) return values;
        Constructor<?> constructor = cls.getDeclaredConstructor(types.toArray(Class<?>[]::new));
        constructor.setAccessible(true); return constructor.newInstance(arguments.toArray());
    }

    @AfterAll void saveEvidenceAndClose() throws Exception {
        try {
            if (inspector == null) return;
            var report = new ImportReport(1, UUID.randomUUID(), catalog.policy().version(), inspector.inspect().fingerprint(),
                    "REGRESSION", "MOCKMVC", System.getProperty("permission.review.source", "DuAnTTCS_N5(6).zip / local compiled source"),
                    System.getProperty("permission.review.executedBy", System.getProperty("user.name")), started,
                    OffsetDateTime.now(), List.copyOf(evidence));
            Path output = Path.of("target/permission-review/regression.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } finally { if (context != null) context.close(); }
    }
}
````

## backend/src/test/java/com/duanttcsn5/library/FullPermissionDatabaseTest.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Full real-controller/service/JWT/PostgreSQL regression. Each run uses a unique disposable database; transactions execute exactly as in the application. */
@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false",
        "spring.main.allow-bean-definition-overriding=true","app.mail.retry-delay-ms=0","spring.datasource.hikari.connection-init-sql=SET lock_timeout='3s'; SET statement_timeout='15s'"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPermissionDatabaseTest {
    public static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            validate(context.getEnvironment().getProperty("spring.datasource.url",""),System.getenv("PERMISSION_REVIEW_LIVE_DB"));
        }
        public static void validate(String url,String liveDatabase) {
            if(!url.startsWith("jdbc:postgresql://"))throw new IllegalStateException("S4-09.2 yêu cầu PostgreSQL thử riêng.");
            java.net.URI uri=java.net.URI.create(url.substring(5));
            String name=Optional.ofNullable(uri.getPath()).orElse("").replaceFirst("^/","");
            if(!List.of("127.0.0.1","localhost").contains(uri.getHost()) || uri.getPort()!=5433
                    || !name.matches("s4092_review_[a-f0-9]{32}") || liveDatabase==null || liveDatabase.isBlank() || name.equals(liveDatabase))
                throw new IllegalStateException("S4-09.2 chỉ chạy trên database thử riêng s4092_review_<UUID> ở localhost:5433, khác database ứng dụng được khai báo.");
        }
    }
    @TestConfiguration
    static class SafeTransport {
        @Bean @Primary JavaMailSender permissionReviewMailSender() {
            return (JavaMailSender)java.lang.reflect.Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),new Class[]{JavaMailSender.class},(proxy,method,args)-> {
                if(method.getName().equals("createMimeMessage"))return new MimeMessage(Session.getInstance(new Properties()));
                if(method.getName().equals("toString"))return "S4-09.2 in-memory mail transport";
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                return null;
            });
        }
        @Bean(name="mailTaskExecutor") java.util.concurrent.Executor synchronousMail() {return Runnable::run;}
        @Bean(name="pickupNotificationExecutor") ThreadPoolTaskExecutor synchronousPickup() {
            return new ThreadPoolTaskExecutor(){@Override public void execute(Runnable task){task.run();}};
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PermissionReviewCatalog catalog;
    @Autowired PermissionReviewInspector inspector;
    MockMvc mvc; PermissionReviewApiFixtures fixtures; Connection lock;
    final List<EvidenceCase> checks=new ArrayList<>(); OffsetDateTime started;
    UUID runKey; String fingerprint;
    @BeforeAll void setup() throws Exception {
        started=OffsetDateTime.now();
        runKey=UUID.fromString(Optional.ofNullable(System.getenv("PERMISSION_REVIEW_RUN_KEY")).orElse(UUID.randomUUID().toString()));
        lock=Objects.requireNonNull(jdbc.getDataSource()).getConnection();
        try(var s=lock.createStatement();var rs=s.executeQuery("SELECT pg_try_advisory_lock(4092,1)")){rs.next();assertTrue(rs.getBoolean(1),"Database thử đang được lần kiểm thử khác sử dụng.");}
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        fixtures=new PermissionReviewApiFixtures(context,mvc,jdbc,mapper,catalog,inspector);
        fixtures.users();fingerprint=inspector.inspect().fingerprint();fixtures.fingerprint=fingerprint;
        assertTrue(inspector.inspect().undocumentedApis().isEmpty(),"Có API chưa được quy định trong tài liệu: "+inspector.inspect().undocumentedApis());
    }
    @TestFactory Stream<DynamicTest> everyMethodAndPathWithFourRolesAndAnonymous() {
        String filter=System.getProperty("permission.review.filter","");
        return catalog.policy().entries().stream().filter(e->filter.isBlank() || e.id().matches(filter))
                .flatMap(entry->entry.httpMethods().stream().flatMap(method->Stream.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")
                        .map(role->DynamicTest.dynamicTest(method+" "+entry.id()+" / "+role,()->probe(entry,method,role,"STANDARD")))));
    }
    @TestFactory Stream<DynamicTest> overrideAndOwnershipCannotBeBypassedByChangingIdentifiers() {
        String filter=System.getProperty("permission.review.filter","");
        return catalog.policy().entries().stream().filter(e->filter.isBlank() || e.id().matches(filter)).flatMap(entry->entry.httpMethods().stream().flatMap(method->
                entry.rules().entrySet().stream().filter(rule->Set.of("OWN","OVERRIDE").contains(rule.getValue())).map(rule->{
                    String scenario=rule.getValue().equals("OVERRIDE")?"OVERRIDE":Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(entry.id())?"OTHER_OWNER":"SPOOFED_OWNER";
                    return DynamicTest.dynamicTest(method+" "+entry.id()+" / "+rule.getKey()+" / "+scenario,()->probe(entry,method,rule.getKey(),scenario));
                })));
    }
    void probe(PolicyEntry entry,String method,String role,String scenario) throws Exception {
        System.out.println("CHECK "+entry.id()+" "+method+" "+role+" "+scenario);
        Throwable[] failure={null};
        {
            try {
                fixtures.prepare(entry,role,scenario);
                var request=fixtures.request(entry,method,role,scenario);
                int expected=catalog.requiredStatus(entry,role,scenario,fixtures.successStatus(entry));
                String before=fixtures.snapshot(); OffsetDateTime at=OffsetDateTime.now();
                MvcResult result=mvc.perform(request).andReturn();int actual=result.getResponse().getStatus();
                boolean denied=Set.of(401,403,404,405).contains(expected);
                Boolean unchanged=denied?before.equals(fixtures.snapshot()):null;
                boolean assertions=false;
                try {
                    assertEquals(expected,actual,result.getResponse().getContentAsString()+"; resolved="+result.getResolvedException());
                    if(denied)assertEquals(Boolean.TRUE,unchanged,"Yêu cầu bị từ chối đã làm thay đổi dữ liệu.");
                    else fixtures.verify(entry,role,scenario,result);
                    assertions=true;
                } catch(Throwable error){failure[0]=error;}
                var historical=historicalFinding(entry.id(),method,role);
                Integer beforeStatus=historical==null?null:historical.get("observedStatus").asInt();
                OffsetDateTime detected=historical==null?at:OffsetDateTime.parse(historical.get("verifiedAt").asText());
                checks.add(new EvidenceCase(entry.id(),role,scenario,expected,beforeStatus,actual,unchanged,detected,OffsetDateTime.now(),
                        "MockMvc với controller, validation, service, JWT ký thật và PostgreSQL 15; fixture riêng cho từng ca; database tạm bị xóa sau lần chạy. "
                        +method+" "+entry.path()+". Đối chiếu toàn bộ bảng nghiệp vụ khi bị từ chối; email dùng transport trong bộ nhớ."
                        +(entry.module().equals("REVIEW")?" Tác vụ khởi chạy lồng nhau chỉ ghi nhận yêu cầu; không tạo tiến trình Maven con trong test.":""),
                        historical==null?null:historical.get("correction").asText(),method,assertions));
            } catch(Throwable error){failure[0]=error;}
        }
        if(failure[0]!=null)throw new AssertionError(entry.id()+" / "+role+" / "+scenario+": "+failure[0].getMessage(),failure[0]);
    }
    tools.jackson.databind.JsonNode historicalFinding(String endpoint,String method,String role) throws Exception {
        try(var input=getClass().getResourceAsStream("/permissions/S4-09.2-fixes.json")) {
            if(input==null)return null;
            for(var item:mapper.readTree(input).get("findings"))
                if(item.get("endpointId").asText().equals(endpoint) && item.get("httpMethod").asText().equals(method) && item.get("role").asText().equals(role))return item;
            return null;
        }
    }
    @AfterAll void evidence() throws Exception {
        try {
            if(fingerprint!=null && !checks.isEmpty()) {
                var report=new ImportReport(2,runKey,catalog.policy().version(),fingerprint,"REGRESSION","ISOLATED_POSTGRES",
                        "FullPermissionDatabaseTest / real JWT + PostgreSQL / disposable database","Bộ kiểm thử toàn hệ thống S4-09.2",started,OffsetDateTime.now(),List.copyOf(checks));
                Path output=Path.of(Optional.ofNullable(System.getenv("PERMISSION_REVIEW_RESULT")).orElse("target/permission-review/full-regression.json"));
                Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            }
        } finally {if(lock!=null)lock.close();}
    }
}
````

## backend/src/test/java/com/duanttcsn5/library/PermissionReviewApiFixtures.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Explicit valid business fixtures. Never executed against the application's live database. */
class PermissionReviewApiFixtures {
    final WebApplicationContext context;final MockMvc mvc;final JdbcTemplate jdbc;final ObjectMapper mapper;
    final PermissionReviewCatalog catalog;final PermissionReviewInspector inspector;
    final Map<String,Long> users=new LinkedHashMap<>();final Map<String,String> tokens=new HashMap<>(),refresh=new HashMap<>();
    final Map<String,Object> values=new HashMap<>();List<String> tables; String encodedPassword; String fingerprint;
    int dateOffset=100; String password="S4092-Review-Only!",suffix,cardNumber,freeBarcode,borrowedBarcode;long reader,staff,manager;
    PermissionReviewApiFixtures(WebApplicationContext context,MockMvc mvc,JdbcTemplate jdbc,ObjectMapper mapper,PermissionReviewCatalog catalog,PermissionReviewInspector inspector) {
        this.context=context;this.mvc=mvc;this.jdbc=jdbc;this.mapper=mapper;this.catalog=catalog;this.inspector=inspector;
    }
    void users() throws Exception {
        String hash=context.getBean(PasswordEncoder.class).encode(password);encodedPassword=hash;
        for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","OTHER_READER")) {
            String email="s4092-"+UUID.randomUUID()+"@example.invalid";
            long id=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id","S4092 "+role,email,hash,role.equals("OTHER_READER")?"READER":role);
            users.put(role,id);
            var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",email,"password",password)))).andReturn();
            assertEquals(200,login.getResponse().getStatus(),login.getResponse().getContentAsString());
            var response=mapper.readTree(login.getResponse().getContentAsString());tokens.put(role,response.get("accessToken").asText());refresh.put(role,response.get("refreshToken").asText());
        }
        tables=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','audit_logs','refresh_tokens','password_reset_requests','reader_registration_attempts') ORDER BY tablename",String.class);
    }
    long id(String sql,Object... args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    String json(Object value){return mapper.writeValueAsString(value);}
    long value(String key){return ((Number)values.get(key)).longValue();}
    String text(String key){return Objects.toString(values.get(key));}
    void prepare(PolicyEntry entry,String role,String scenario) throws Exception {
        values.clear();suffix=UUID.randomUUID().toString().substring(0,12);
        for(String who:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","OTHER_READER")) {
            String code=who.equals("OTHER_READER")?"READER":who;
            String email="s4092-"+who+suffix+"@example.invalid";
            long uid=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id","S4092 "+who,email,encodedPassword,code);
            var user=new com.duanttcsn5.library.entity.User();user.setId(uid);user.setEmail(email);user.setFullName("S4092 "+who);user.setStatus("ACTIVE");user.setTokenVersion(0);
            var roleEntity=new com.duanttcsn5.library.entity.Role();roleEntity.setCode(code);user.setRole(roleEntity);
            users.put(who,uid);tokens.put(who,context.getBean(com.duanttcsn5.library.security.JwtService.class).issueAccessToken(user).value());
        }
        reader=users.get("READER");staff=users.get("LIBRARIAN");manager=users.get("LIBRARY_MANAGER");
        values.put("readerId",reader);values.put("readerUserId",reader);
        long type=id("INSERT INTO card_types(name,duration_months,max_books,loan_days,max_renewals,renewal_days) VALUES (?,12,10,14,3,7) RETURNING id","S4092 "+suffix);values.put("cardTypeId",type);
        for(String who:List.of("READER","OTHER_READER")) {
            long user=users.get(who);
            jdbc.update("INSERT INTO reader_profiles(user_id,date_of_birth,member_code,registration_status) VALUES (?,DATE '2006-01-01',?,'APPROVED')",user,"S4092-"+who+suffix);
            long card=id("INSERT INTO library_cards(user_id,card_type_id,card_number,status,issued_at,expires_at,created_by) VALUES (?,?,?,'ACTIVE',CURRENT_DATE,CURRENT_DATE+365,?) RETURNING id",user,type,"S4092-"+who+suffix,staff);
            if(who.equals("READER")){values.put("cardId",card);cardNumber="S4092-"+who+suffix;}
        }
        long warehouse=id("INSERT INTO warehouses(code,name) VALUES (?,?) RETURNING id","S4092"+suffix,"Kho "+suffix);
        long shelf=id("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,?) RETURNING id",warehouse,"S4092","Kệ thử");values.put("warehouseId",warehouse);values.put("shelfId",shelf);
        long author=id("INSERT INTO authors(name) VALUES (?) RETURNING id","Tác giả "+suffix);
        long category=id("INSERT INTO categories(name) VALUES (?) RETURNING id","Thể loại "+suffix);values.put("authorId",author);values.put("categoryId",category);
        long book=book(author,category,"Sách tự do "+suffix);values.put("bookId",book);
        freeBarcode="S4092-FREE-"+suffix;long free=copy(book,shelf,freeBarcode);values.put("copyId",free);
        long borrowedBook=book(author,category,"Sách đang mượn "+suffix);values.put("borrowedBookId",borrowedBook);
        borrowedBarcode="S4092-LOAN-"+suffix;long borrowedCopy=copy(borrowedBook,shelf,borrowedBarcode);values.put("borrowedCopyId",borrowedCopy);
        long loan=id("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id","S4092-"+suffix,reader,staff);values.put("loanId",loan);
        long item=id("INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date) VALUES (?,?,CURRENT_TIMESTAMP-interval '2 days',CURRENT_TIMESTAMP+interval '5 days') RETURNING id",loan,borrowedCopy);values.put("itemId",item);
        long holdBook=book(author,category,"Sách chờ nhận "+suffix);long heldCopy=copy(holdBook,shelf,"S4092-HOLD-"+suffix);
        long reservation=id("INSERT INTO book_reservations(book_id,reader_id,status,book_copy_id,reserved_at,pickup_deadline,hold_started_at) VALUES (?,?,'READY_FOR_PICKUP',?,CURRENT_TIMESTAMP-interval '1 hour',CURRENT_TIMESTAMP+interval '2 days',CURRENT_TIMESTAMP-interval '30 minutes') RETURNING id",holdBook,reader,heldCopy);
        jdbc.update("UPDATE book_copies SET status='HELD' WHERE id=?",heldCopy);values.put("reservationId",reservation);values.put("heldCopyId",heldCopy);
        values.put("id",reader);values.put("requestId",UUID.randomUUID().toString());
        if(entry.controller().equals("AuthorController"))values.put("id",author);
        if(entry.controller().equals("CategoryController"))values.put("id",category);
        if(entry.controller().equals("CardTypeController"))values.put("id",type);
        if(entry.controller().equals("BookCatalogController") || entry.controller().equals("BookCoverController"))values.put("id",book);
        if(entry.controller().equals("BookCopyController"))values.put("id",free);
        if(entry.controller().equals("LibraryConfigurationController"))values.put("id",entry.handler().contains("Warehouse")?warehouse:shelf);
        if(entry.controller().equals("UserManagementController"))values.put("id",id("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Nhân viên thử',?,'ACTIVE' FROM roles WHERE code='LIBRARIAN' RETURNING id",suffix+"@example.invalid"));
        specialized(entry,role,scenario);
    }
    long book(long author,long category,String title){return id("INSERT INTO books(title,author_id,category_id,publisher,publication_year,page_count) VALUES (?,?,?,'NXB Trẻ',2024,100) RETURNING id",title,author,category);}
    long copy(long book,long shelf,String barcode){return id("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,?,'AVAILABLE',CURRENT_DATE-10,50000,'GOOD') RETURNING id",book,barcode,shelf);}
    void specialized(PolicyEntry e,String role,String scenario) throws Exception {
        String id=e.id();
        if(id.equals("AuthorController.deleteAuthor") || id.equals("AuthorController.toggleStatus"))values.put("id",id("INSERT INTO authors(name) VALUES (?) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("CategoryController.deleteCategory") || id.equals("CategoryController.toggleStatus"))values.put("id",id("INSERT INTO categories(name) VALUES (?) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("CardTypeController.deleteCardType") || id.equals("CardTypeController.toggleStatus"))values.put("id",id("INSERT INTO card_types(name,duration_months,max_books,loan_days,max_renewals,renewal_days) VALUES (?,12,10,14,3,7) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("LibraryConfigurationController.deleteShelf"))values.put("id",id("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,'Kệ chưa dùng') RETURNING id",value("warehouseId"),suffix));
        if(id.contains("ClosedDate")) {
            LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(dateOffset+=2); values.put("closedDate",day.plusDays(1));
            values.put("id",id("INSERT INTO library_closed_dates(closed_date,reason) VALUES (?,'Ngày nghỉ thử') RETURNING id",day));
        }
        if(id.equals("LibraryCardController.approve") || id.equals("LibraryCardController.reject")) {
            long pending=id("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Bạn đọc chờ duyệt',?,'PENDING' FROM roles WHERE code='READER' RETURNING id",suffix+"@example.invalid");
            jdbc.update("INSERT INTO reader_profiles(user_id,date_of_birth,member_code,registration_status) VALUES (?,DATE '2006-01-01',?,'PENDING')",pending,suffix);values.put("readerUserId",pending);
        }
        if(id.equals("BookCopyController.repair"))jdbc.update("UPDATE book_copies SET status='AVAILABLE',physical_condition='LIGHTLY_DAMAGED' WHERE id=?",value("copyId"));
        if(e.controller().equals("BookCoverController"))context.getBean(BookCoverService.class).upload(value("bookId"),image("file"));
        if(id.equals("LoanController.rejectionDetail"))values.put("id",id("INSERT INTO loan_rejections(request_id,source,reader_user_id,reader_name,card_number,actor_user_id,actor_name,borrowed_books,max_books,overdue_loans,unpaid_amount_vnd) VALUES (?,'CARD_CHECK',?,'Bạn đọc thử',?,?,'Thủ thư thử',1,10,0,0) RETURNING id",UUID.randomUUID(),reader,cardNumber,staff));
        if(e.controller().equals("OverdueContactController") || id.equals("LoanController.overdueLoans") || id.toLowerCase(Locale.ROOT).contains("suggestion"))jdbc.update("UPDATE loan_items SET due_date=CURRENT_TIMESTAMP-interval '40 days' WHERE id=?",value("itemId"));
        if(id.toLowerCase(Locale.ROOT).contains("suggestion") || id.equals("LibraryCardController.suggestionDetail")) {
            context.getBean(CardLockSuggestionService.class).refresh();
            values.put("id",jdbc.queryForObject("SELECT id FROM card_lock_suggestions WHERE card_id=? AND status='PENDING'",Long.class,value("cardId")));
        }
        if(id.equals("LibraryCardController.unlockCard")){
            var response=call(post("/api/v1/library-cards/"+value("cardId")+"/lock").contentType("application/json").content(json(Map.of("reason","Khóa thử"))),"LIBRARY_MANAGER");
            values.put("lockStartedAt",jdbc.queryForObject("SELECT manual_lock_started_at FROM library_cards WHERE id=?",OffsetDateTime.class,value("cardId")).toString());
        }
        if(e.controller().equals("FeePaymentController") || e.controller().equals("FeeReductionController"))fees(e);
        if(e.controller().equals("BookIncidentController"))incidents(e);
        if(id.equals("AuthController.refresh")) {
            String who=role.equals("ANONYMOUS")?"READER":role;
            var login=mapper.readTree(call(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.get(who)),"password",password))),who));
            refresh.put(who,login.get("refreshToken").asText());
        }
        if(e.controller().equals("InitialPasswordController") || e.controller().equals("PasswordResetController"))passwordToken(e);
        if(e.controller().equals("PermissionReviewController"))reviewReport(e);
        if(e.controller().equals("AuditLogController"))values.put("id",id("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id) VALUES (?,'S4092_TEST','TEST','1') RETURNING id",staff));
        if(e.controller().equals("LoanReminderController") && (id.endsWith("retrySingle") || id.endsWith("retryPending")))reminder(e);
        if(id.equals("BookReservationController.reserve") || id.equals("BookReservationController.reserveMany")) {
            // Reserve a title for which this reader has no existing reservation.
            values.put("bookId",value("bookId"));
        }
        if(scenario.equals("OVERRIDE"))jdbc.update("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'Khoản phí cho luồng ngoại lệ thử',5000)",reader);
        if(id.equals("BookReservationController.getLatestAutoCancelRun"))context.getBean(ReservationAutoCancellationService.class).executeAutoCancellationRun(OffsetDateTime.now(),"S4092 fixture");
        if(id.equals("ReadyPickupNotificationController.runCancellation"))jdbc.update("UPDATE book_reservations SET reserved_at=CURRENT_TIMESTAMP-interval '3 days',hold_started_at=CURRENT_TIMESTAMP-interval '2 days',pickup_deadline=CURRENT_TIMESTAMP-interval '1 hour' WHERE id=?",value("reservationId"));
        if(e.controller().equals("LoanReminderController"))jdbc.update("UPDATE loan_items SET due_date=CURRENT_TIMESTAMP WHERE id=?",value("itemId"));
        if(e.rules().containsValue("OWN")) ownershipFixtures(e,scenario);
    }
    void ownershipFixtures(PolicyEntry entry,String scenario) {
        long other=users.get("OTHER_READER");
        String marker="FOREIGN-"+suffix;values.put("foreignMarker",marker);
        long foreignBook=book(value("authorId"),value("categoryId"),marker);
        long foreignCopy=copy(foreignBook,value("shelfId"),marker);
        long foreignLoan=id("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id",marker,other,staff);
        long foreignItem=id("INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date) VALUES (?,?,CURRENT_TIMESTAMP-interval '2 days',CURRENT_TIMESTAMP+interval '5 days') RETURNING id",foreignLoan,foreignCopy);
        long foreignReservation=id("INSERT INTO book_reservations(book_id,reader_id,status,reserved_at) VALUES (?,?,'PENDING',CURRENT_TIMESTAMP) RETURNING id",foreignBook,other);
        values.put("foreignPasswordHash",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,other));
        if(entry.id().equals("LoanController.myReturnedBooks")) {
            jdbc.update("UPDATE loan_items SET returned_at=CURRENT_TIMESTAMP,returned_by=?,returned_by_name='Thủ thư thử' WHERE id IN (?,?)",staff,value("itemId"),foreignItem);
        }
        if(scenario.equals("OTHER_OWNER")) {
            if(entry.id().equals("BookReservationController.cancelMine"))values.put("reservationId",foreignReservation);
            else if(entry.id().equals("LoanController.checkMyRenewal"))values.put("itemId",foreignItem);
            else values.put("loanId",foreignLoan);
        }
    }
    void fees(PolicyEntry e) throws Exception {
        long fee;
        if(e.controller().equals("FeeReductionController")){
            jdbc.update("UPDATE loan_items SET returned_at=CURRENT_TIMESTAMP,returned_by=?,returned_by_name='Thủ thư thử' WHERE id=?",staff,value("itemId"));
            var due=OffsetDateTime.now().minusDays(3);var now=OffsetDateTime.now();
            var assessment=new ReturnFeeAssessment("READY","Phí thử","S4-01.1-OPEN-DAYS-HALF-UP-V1",due,now,3,5000L,15000L,15000L,null,List.of(),List.of(),List.of());
            fee=context.getBean(OverdueFeeRepository.class).record(value("itemId"),reader,assessment).feeId();
            if(!e.handler().equals("create")){
                long request=id("INSERT INTO fee_reduction_requests(reader_fee_id,requested_by,requester_name,original_amount_vnd,requested_amount_vnd,reason) VALUES (?,?,'Thủ thư thử',15000,5000,'Miễn giảm thử') RETURNING id",fee,staff);values.put("requestId",request);
            }
        } else fee=id("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'Khoản phí thử',10000) RETURNING id",reader);
        values.put("feeId",fee);
        if(e.controller().equals("FeePaymentController")) {
            String detail=call(get("/api/v1/fee-payments/readers/"+reader),"LIBRARIAN");var tree=mapper.readTree(detail);
            values.put("debtToken",tree.get("debtToken").asText());values.put("expectedTotalVnd",tree.get("totalOutstandingVnd").asLong());
            if(e.handler().equals("receipt")) {
                String payment=call(post("/api/v1/fee-payments/readers/"+reader+"/collect-full").contentType("application/json").content(json(payment(false))),"LIBRARIAN");
                values.put("paymentId",mapper.readTree(payment).get("id").asLong());
            }
        }
    }
    Map<String,Object> payment(boolean partial){var map=new LinkedHashMap<String,Object>();map.put("requestKey",UUID.randomUUID());map.put("expectedTotalVnd",values.get("expectedTotalVnd"));map.put("debtToken",values.get("debtToken"));if(partial)map.put("amountVnd",5000);return map;}
    void incidents(PolicyEntry e) throws Exception {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=1,damaged_factor=0.5 WHERE id=1");
        if(Set.of("search","preview","confirm","factors","update","history").contains(e.handler()))return;
        var preview=mapper.readTree(call(get("/api/v1/book-incidents/preview").param("itemId",text("itemId")).param("type","LOST"),"LIBRARIAN"));
        String body=call(multipart("/api/v1/book-incidents").file(image("files")).param("itemId",text("itemId")).param("type","LOST").param("description","Báo mất sách thử").param("previewToken",preview.get("token").asText()),"LIBRARIAN");
        var incident=mapper.readTree(body);values.put("id",incident.get("id").asLong());values.put("imageId",incident.get("photos").get(0).get("id").asLong());
        if(e.handler().equals("confirmAdjustment")) {
            var adjustment=mapper.readTree(call(post("/api/v1/book-incidents/"+text("id")+"/adjustments").contentType("application/json").content(json(Map.of("reason","Tìm lại sách thử","caseType","FOUND","targetStatus","AVAILABLE"))),"LIBRARIAN"));
            values.put("adjustmentId",adjustment.get("id").asLong());
        }
    }
    void passwordToken(PolicyEntry e) throws Exception {
        String token="s4092-token-"+suffix;String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        long account=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,'Tài khoản liên kết thử',?,?,'"+(e.controller().equals("InitialPasswordController")?"PENDING":"ACTIVE")+"' FROM roles WHERE code='LIBRARIAN' RETURNING id",suffix+"@example.invalid",context.getBean(PasswordEncoder.class).encode(password));
        jdbc.update("INSERT INTO password_reset_requests(user_id,requested_email,token_hash,request_type,expires_at) SELECT id,email,?,?,CURRENT_TIMESTAMP+interval '1 hour' FROM users WHERE id=?",hash,e.controller().equals("InitialPasswordController")?"INITIAL_PASSWORD":"RESET_PASSWORD",account);values.put("token",token);
    }
    void reminder(PolicyEntry e) {
        values.put("id",id("INSERT INTO loan_reminder_logs(reminder_date,reader_id,reader_name,recipient_email,reminder_group,loan_item_count,loan_numbers,book_titles,subject,email_content,status,error_message) SELECT CURRENT_DATE,id,full_name,email,'DUE_TODAY',1,'S4092','Sách thử','Nhắc trả sách thử','Nội dung thử','FAILED','Lỗi thử để kiểm tra gửi lại' FROM users WHERE id=? RETURNING id",reader));
    }
    void reviewReport(PolicyEntry entry) throws Exception {
        List<EvidenceCase> evidence=new ArrayList<>();OffsetDateTime at=OffsetDateTime.now().minusSeconds(1);
        for(var e:catalog.policy().entries())for(String method:e.httpMethods()) {
            if(!entry.handler().equals("complete") && !e.id().equals("AuthController.me"))continue;
            for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")) {
                String scenario="STANDARD";
                int expected=catalog.requiredStatus(e,role,scenario,successStatus(e));
                evidence.add(new EvidenceCase(e.id(),role,scenario,expected,null,expected,true,at,at,"Fixture biên bản đầy đủ để kiểm tra API quản trị; không dùng làm bằng chứng của lần chạy thực tế.",null,method,true));
                if("OVERRIDE".equals(e.rules().get(role))) {
                    evidence.add(new EvidenceCase(e.id(),role,"OVERRIDE",200,null,200,null,at,at,"Fixture điều kiện luồng ngoại lệ.",null,method,true));
                }
                if("OWN".equals(e.rules().get(role))) {
                    String scope=Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(e.id())?"OTHER_OWNER":"SPOOFED_OWNER";int code=scope.equals("OTHER_OWNER")?404:expected;
                    evidence.add(new EvidenceCase(e.id(),role,scope,code,null,code,true,at,at,"Fixture kiểm tra điều kiện biên bản.",null,method,true));
                }
            }
        }
        var report=new ImportReport(2,UUID.randomUUID(),catalog.policy().version(),fingerprint,"REGRESSION","ISOLATED_POSTGRES","Fixture biên bản kiểm thử điều kiện API quản trị","Người kiểm thử",at.minusSeconds(1),OffsetDateTime.now(),evidence);
        String saved=call(post("/api/v1/permission-review/reports").contentType("application/json").content(json(report)),"ADMIN");
        long id=mapper.readTree(saved).get("summary").get("id").asLong();values.put("id",id);values.put("report",report);values.put("runKey",report.runKey());
        jdbc.update("INSERT INTO permission_review_jobs(run_key,state,message,requested_by_user_id,requested_by_name,fingerprint,finished_at,report_id) VALUES (?,'SUCCEEDED','Fixture hoàn tất',?,'Quản trị thử',?,CURRENT_TIMESTAMP,?)",report.runKey(),users.get("ADMIN"),report.fingerprint(),id);
    }
    MockMultipartFile image(String field) throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);
        return new MockMultipartFile(field,"s4092.png","image/png",output.toByteArray());
    }
    String call(AbstractMockHttpServletRequestBuilder<?> request,String role) throws Exception {
        var result=mvc.perform(request.header("Authorization","Bearer "+tokens.get(role))).andReturn();
        assertTrue(result.getResponse().getStatus()>=200 && result.getResponse().getStatus()<300,"Fixture API failed "+result.getResponse().getStatus()+": "+result.getResponse().getContentAsString());
        return result.getResponse().getContentAsString();
    }
    AbstractMockHttpServletRequestBuilder<?> request(PolicyEntry e,String method,String role,String scenario) throws Exception {
        String path=e.path();for(var item:values.entrySet())path=path.replace("{"+item.getKey()+"}",Objects.toString(item.getValue()));
        if(path.contains("{"))throw new IllegalArgumentException("Chưa có fixture đường dẫn: "+path);
        AbstractMockHttpServletRequestBuilder<?> request;
        if(e.id().equals("BookCoverController.upload"))request=multipart(path).file(image("file"));
        else if(e.id().equals("BookIncidentController.confirm")) {
            var preview=mapper.readTree(call(get("/api/v1/book-incidents/preview").param("itemId",text("itemId")).param("type","LOST"),"LIBRARIAN"));
            request=multipart(path).file(image("files")).param("itemId",text("itemId")).param("type","LOST").param("description","Báo mất sách thử").param("previewToken",preview.get("token").asText());
        } else request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path);
        if(!role.equals("ANONYMOUS"))request.header("Authorization","Bearer "+tokens.get(role));
        if(scenario.equals("SPOOFED_OWNER"))request.param("readerId",users.get("OTHER_READER").toString()).param("userId",users.get("OTHER_READER").toString());
        boolean override=scenario.equals("OVERRIDE");
        Object body=null;
        switch(e.id()) {
            case "AuthController.login" -> {String who=role.equals("ANONYMOUS")?"READER":role;body=Map.of("email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.get(who)),"password",password);}
            case "AuthController.refresh" -> body=Map.of("refreshToken",refresh.get(role.equals("ANONYMOUS")?"READER":role));
            case "InitialPasswordController.validateToken", "PasswordResetController.validateResetPasswordToken" -> request.param("token",text("token"));
            case "InitialPasswordController.setInitialPassword" -> body=Map.of("token",text("token"),"password","NewReview123!");
            case "PasswordResetController.resetPassword" -> body=Map.of("token",text("token"),"newPassword","NewReview123!","confirmPassword","NewReview123!");
            case "PasswordResetController.forgotPassword" -> body=Map.of("email",suffix+"@example.invalid");
            case "ReaderController.register" -> body=Map.of("fullName","Bạn đọc đăng ký thử","email",suffix+"@example.invalid","dateOfBirth","2006-01-01","phone","0912345678","address","Địa chỉ thử nghiệm","password","ReviewTest123!");
            case "ReaderController.checkDuplicate" -> request.param("email",suffix+"@example.invalid");
            case "ReaderController.updateMyContact" -> body=Map.of("phone","0912345678","address","Địa chỉ cập nhật thử","email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,reader),"currentPassword",password);
            case "ReaderController.changeMyPassword" -> body=Map.of("currentPassword",password,"newPassword","NewReview123!","confirmPassword","NewReview123!");
            case "AuthorController.createAuthor","AuthorController.updateAuthor" -> body=Map.of("name","Tác giả cập nhật "+suffix,"note","Ghi chú thử");
            case "CategoryController.createCategory","CategoryController.updateCategory" -> body=Map.of("name","Thể loại cập nhật "+suffix,"description","Mô tả thử");
            case "LibraryPublisherController.create" -> body=Map.of("name","Nhà xuất bản thử "+suffix);
            case "BookCatalogController.catalogBook" -> body=Map.of("title","Sách biên mục mới "+suffix,"authorIds",List.of(value("authorId")),"categoryId",value("categoryId"),"publisher","NXB Trẻ","publicationYear",2024,"pageCount",120,"confirmDuplicateTitle",false);
            case "CardTypeController.createCardType","CardTypeController.updateCardType" -> body=Map.of("name","Loại thẻ cập nhật "+suffix,"duration",12,"maxBooks",10,"loanDays",14,"maxRenewals",3,"renewalDays",7);
            case "UserManagementController.createAccount","UserManagementController.updateAccount" -> body=Map.of("fullName","Nhân viên cập nhật","email",suffix+"new@example.invalid","phone","0912345678","role","LIBRARIAN");
            case "UserManagementController.updateAccountStatus" -> body=Map.of("status","LOCKED");
            case "LibraryCardController.approve" -> body=Map.of("cardTypeId",value("cardTypeId"),"expiresAt",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(6));
            case "LibraryCardController.reject" -> body=Map.of("reason","Hồ sơ thử chưa đủ điều kiện");
            case "LibraryCardController.lockCard","LibraryCardController.acceptSuggestion" -> body=Map.of("reason","Khóa thẻ theo hồ sơ thử");
            case "LibraryCardController.ignoreSuggestion" -> body=Map.of("reason","Bỏ qua đề xuất thử");
            case "LibraryCardController.unlockCard" -> body=Map.of("reason","Đã xử lý hồ sơ thử","lockStartedAt",text("lockStartedAt"));
            case "BookCopyController.create" -> body=Map.of("barcodeMode","MANUAL","barcode","S4092-NEW-"+suffix,"warehouseId",value("warehouseId"),"shelfId",value("shelfId"),"receivedDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),"coverPrice",50000,"physicalCondition","GOOD");
            case "BookCopyController.previewBulk","BookCopyController.createBulk" -> {
                request.param("quantity","2").param("warehouseId",text("warehouseId")).param("shelfId",text("shelfId"));
                var bulk=new LinkedHashMap<String,Object>();bulk.put("quantity",2);bulk.put("warehouseId",value("warehouseId"));bulk.put("shelfId",value("shelfId"));bulk.put("receivedDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                if(e.handler().equals("createBulk")) {
                    var preview=mapper.readTree(call(get("/api/v1/books/"+text("bookId")+"/copies/bulk/preview").param("quantity","2").param("warehouseId",text("warehouseId")).param("shelfId",text("shelfId")),"LIBRARIAN"));
                    bulk.put("confirmed",true);bulk.put("expectedStartNumber",preview.get("startNumber").asLong());bulk.put("expectedSkippedBarcodes",List.of());
                }body=bulk;
            }
            case "BookCopyController.update" -> body=Map.of("warehouseId",value("warehouseId"),"shelfId",value("shelfId"),"physicalCondition","GOOD","notes","Ghi chú đã cập nhật");
            case "BookCopyController.repair" -> body=Map.of("reason","Đã sửa chữa xong");
            case "LoanController.searchLoans" -> request.param("code",cardNumber);
            case "FeePaymentController.search" -> request.param("query",cardNumber);
            case "PickupLastDayReminderController.preview" -> request.param("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
            case "PickupLastDayReminderController.run" -> body=Map.of("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            case "LoanController.readerEligibility" -> request.param("cardNumber",cardNumber);
            case "LoanController.checkReaderAndLog" -> body=Map.of("cardNumber",cardNumber,"requestId",UUID.randomUUID());
            case "LoanController.previewDirectLoanItem" -> body=Map.of("cardNumber",cardNumber,"barcode",freeBarcode,"selectedBarcodes",List.of(),"overridePreview",override);
            case "LoanController.createDirectLoan" -> body=Map.of("cardNumber",cardNumber,"barcodes",List.of(freeBarcode),"requestId",UUID.randomUUID(),"overrideRequested",override,"overrideReason",override?"Ngoại lệ có lý do theo bài kiểm thử":"");
            case "LoanController.createFromReservation" -> body=reservationConfirmation(override);
            case "LoanController.createFromReservations" -> body=Map.of("reservationIds",List.of(value("reservationId")),"confirmation",reservationConfirmation(override));
            case "LoanController.lookupReturn" -> request.param("barcode",borrowedBarcode);
            case "LoanController.confirmReturn" -> body=Map.of("barcode",borrowedBarcode,"itemId",value("itemId"));
            case "BookReservationController.reserveMany" -> body=Map.of("quantity",1);
            case "BookReservationController.cancelByStaff" -> body=Map.of("reason","Hủy theo dữ liệu thử");
            case "FeePaymentController.collect" -> body=payment(false);
            case "FeePaymentController.collectPartial" -> body=payment(true);
            case "FeeReductionController.create" -> body=Map.of("amountVnd",5000,"reason","Đề nghị miễn giảm thử");
            case "FeeReductionController.reject" -> body=Map.of("reason","Chưa đủ điều kiện miễn giảm");
            case "OverdueContactController.record" -> body=Map.of("note","Đã liên hệ nhắc trả trong ca thử");
            case "LibraryConfigurationController.createWarehouse","LibraryConfigurationController.updateWarehouse" -> body=Map.of("code","UPD"+suffix,"name","Kho cập nhật thử "+suffix,"description","Mô tả thử");
            case "LibraryConfigurationController.createShelf","LibraryConfigurationController.updateShelf" -> body=Map.of("warehouseId",value("warehouseId"),"code","UPD"+suffix,"name","Kệ cập nhật thử","description","Mô tả thử");
            case "LibraryConfigurationController.createClosedDate","LibraryConfigurationController.updateClosedDate" -> body=Map.of("closedDate",values.get("closedDate"),"reason","Ngày nghỉ thử cập nhật");
            case "LibraryConfigurationController.createClosedDatesBulk" -> body=Map.of("dates",List.of(Map.of("closedDate",values.get("closedDate"),"reason","Ngày nghỉ thử")));
            case "LibraryConfigurationController.updateWeeklySchedule" -> body=Map.of("days",java.util.stream.IntStream.rangeClosed(1,7).mapToObj(day->Map.of("dayOfWeek",day,"open",true,"openTime","07:00","closeTime","18:00")).toList());
            case "LibraryConfigurationController.adjustDueDate" -> request.param("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(14).toString());
            case "LibraryCardController.updateSuggestionSettings" -> body=Map.of("debtThresholdVnd",100000);
            case "LibraryConfigurationController.updateOverdueFee" -> body=Map.of("dailyRateVnd",5000);
            case "BookIncidentController.update" -> body=Map.of("lost",1.5,"damaged",0.5);
            case "BookIncidentController.preview" -> request.param("itemId",text("itemId")).param("type","LOST");
            case "BookIncidentController.adjustmentPreview" -> request.param("targetStatus","AVAILABLE");
            case "BookIncidentController.requestAdjustment" -> body=Map.of("reason","Tìm lại bản sách thử","caseType","FOUND","targetStatus","AVAILABLE");
            case "LoanReminderController.triggerReminders" -> body=Map.of("targetDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),"simulateFailure",false);
            case "LoanReminderController.retryPending","LoanReminderController.retrySingle" -> body=Map.of("simulateFailure",false,"logId",value("id"));
            case "PermissionReviewController.importReport" -> {
                var source=(ImportReport)values.get("report");body=new ImportReport(2,UUID.randomUUID(),source.policyVersion(),source.fingerprint(),source.kind(),source.environment(),source.sourceReference(),source.executedBy(),source.startedAt(),source.completedAt(),source.checks());
            }
            default -> { }
        }
        if(e.id().equals("ReaderController.register"))request.with(value->{value.setRemoteAddr("192.0."+((users.get("READER") / 200)%250)+"."+(users.get("READER")%200+1));return value;});
        if(body!=null)request.contentType("application/json").content(json(body));
        return request;
    }
    Map<String,Object> reservationConfirmation(boolean override) {
        return Map.of("cardNumber",cardNumber,"requestId",UUID.randomUUID(),"overrideRequested",override,"overrideReason",override?"Ngoại lệ theo hồ sơ thử":"");
    }
    int successStatus(PolicyEntry e) {
        if(Set.of("BookReservationController.cancelMine","LibraryCardController.reject","BookCoverController.upload",
                "AuthorController.deleteAuthor","CategoryController.deleteCategory","CardTypeController.deleteCardType","UserManagementController.deleteAccount",
                "LibraryConfigurationController.deleteShelf","LibraryConfigurationController.deleteClosedDate").contains(e.id()))return 204;
        if(e.id().equals("BookReservationController.getLatestAutoCancelRun"))return 200;
        if(e.id().equals("PermissionReviewController.startRun"))return 202;
        if(Set.of("LibraryCardController.approve","LoanController.createFromReservation","LoanController.createFromReservations","LoanController.createDirectLoan",
                "BookReservationController.reserve","BookReservationController.reserveMany","FeeReductionController.create","BookCopyController.create","BookCopyController.createBulk",
                "BookIncidentController.confirm","BookIncidentController.requestAdjustment","OverdueContactController.record","ReaderController.register",
                "AuthorController.createAuthor","CategoryController.createCategory","CardTypeController.createCardType","BookCatalogController.catalogBook","LibraryPublisherController.create",
                "LibraryConfigurationController.createWarehouse","LibraryConfigurationController.createShelf","LibraryConfigurationController.createClosedDate","LibraryConfigurationController.createClosedDatesBulk",
                "UserManagementController.createAccount","PermissionReviewController.importReport").contains(e.id()))return 201;
        return 200;
    }
    void verify(PolicyEntry e,String role,String scenario,MvcResult result) throws Exception {
        String body=result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"),"API đã lộ trường mật khẩu.");
        if(e.id().equals("AuthController.me"))assertEquals(users.get(role).longValue(),mapper.readTree(body).get("id").asLong());
        if(e.rules().containsValue("OWN") && role.equals("READER")) {
            assertFalse(body.contains(text("foreignMarker")),"API cá nhân trả về dữ liệu của bạn đọc khác.");
            assertEquals(text("foreignPasswordHash"),jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,users.get("OTHER_READER")),"Yêu cầu cá nhân đã đổi mật khẩu người khác.");
            if(Set.of("LoanController.myBorrowedBooks","LoanController.myReturnedBooks").contains(e.id()))assertTrue(body.contains(suffix),"Danh sách cá nhân không có dữ liệu hợp lệ của chính người gọi.");
        }
        if(scenario.equals("SPOOFED_OWNER")) {
            assertNull(jdbc.queryForObject("SELECT phone FROM users WHERE id=?",String.class,users.get("OTHER_READER")),"Tham số giả mạo đã thay đổi tài khoản khác.");
            if(e.id().equals("ReaderController.updateMyContact"))assertEquals("0912345678",jdbc.queryForObject("SELECT phone FROM users WHERE id=?",String.class,reader));
            if(e.id().equals("ReaderController.getMyProfile"))assertFalse(body.contains("S4092 OTHER_READER"));
            if(e.id().equals("LibraryCardController.getMyCard"))assertTrue(body.contains(cardNumber));
            if(e.id().equals("BookReservationController.getMine"))assertTrue(body.contains(text("reservationId")));
        }
        if(e.id().equals("LoanController.confirmReturn"))assertNotNull(jdbc.queryForObject("SELECT returned_at FROM loan_items WHERE id=?",OffsetDateTime.class,value("itemId")));
        if(e.id().equals("FeePaymentController.collect") || e.id().equals("FeePaymentController.collectPartial"))assertTrue(jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?",Long.class,value("feeId"))>0);
        if(e.id().equals("UserManagementController.deleteAccount"))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,value("id")));
        if(e.id().equals("PermissionReviewController.complete"))assertNotNull(mapper.readTree(body).get("completion"));
        if(e.id().equals("PermissionReviewController.startRun"))assertEquals("STARTING",mapper.readTree(body).get("state").asText());
        if(e.id().equals("OverdueDebtReportController.export"))assertTrue(result.getResponse().getContentAsByteArray().length>100);
    }
    String snapshot() {
        List<String> rows=new ArrayList<>();
        for(String table:tables) {
            if(!table.matches("[a-z0-9_]+"))throw new IllegalStateException("Tên bảng không hợp lệ.");
            rows.add(table+":"+jdbc.queryForObject("SELECT COALESCE(md5(string_agg(rowdata,E'\\n' ORDER BY rowdata)),'') FROM (SELECT to_jsonb(t)::text rowdata FROM public.\""+table+"\" t) data",String.class));
        }
        return String.join("|",rows);
    }
}
````

## backend/src/test/java/com/duanttcsn5/library/PermissionReviewControllerTest.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.PermissionReviewController;
import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.PermissionReviewService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissionReviewControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PermissionReviewController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean com.duanttcsn5.library.service.PermissionReviewRunService runs() { return mock(com.duanttcsn5.library.service.PermissionReviewRunService.class); }
        @Bean com.duanttcsn5.library.service.PermissionReviewCoverageService coverage() { return mock(com.duanttcsn5.library.service.PermissionReviewCoverageService.class); }
        @Bean PermissionReviewService service() { return mock(PermissionReviewService.class); }
        @Bean JwtService jwt() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    PermissionReviewService service;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(PermissionReviewService.class));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    void token(String code) {
        Role role = new Role(); role.setCode(code); User user = new User(); user.setId(1L); user.setRole(role);
        user.setStatus("ACTIVE"); user.setTokenVersion(0);
        when(context.getBean(UserRepository.class).findById(1L)).thenReturn(Optional.of(user));
        when(context.getBean(JwtService.class).decode("review")).thenReturn(Jwt.withTokenValue("review").header("alg", "HS256")
                .subject("1").claim("tokenVersion", 0).build());
    }
    @Test void anonymousCannotReadMatrixReportListOrDetailsAndCannotImport() throws Exception {
        for (String path : List.of("/api/v1/permission-review", "/api/v1/permission-review/reports", "/api/v1/permission-review/reports/1", "/api/v1/permission-review/runs/latest", "/api/v1/permission-review/runs/00000000-0000-0000-0000-000000000001", "/api/v1/permission-review/reports/1/coverage", "/api/v1/permission-review/reports/1/export")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/permission-review/reports").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        for(String path:List.of("/api/v1/permission-review/runs","/api/v1/permission-review/reports/1/complete"))
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void threeOtherRolesReceive403IncludingMalformedImportBody() throws Exception {
        for (String role : List.of("READER", "LIBRARIAN", "LIBRARY_MANAGER")) {
            token(role);
            for (String path : List.of("/api/v1/permission-review", "/api/v1/permission-review/reports", "/api/v1/permission-review/reports/1", "/api/v1/permission-review/runs/latest", "/api/v1/permission-review/runs/00000000-0000-0000-0000-000000000001", "/api/v1/permission-review/reports/1/coverage", "/api/v1/permission-review/reports/1/export")) {
                mvc.perform(get(path).header("Authorization", "Bearer review")).andExpect(status().isForbidden());
            }
            mvc.perform(post("/api/v1/permission-review/reports").header("Authorization", "Bearer review")
                    .contentType("application/json").content("broken")).andExpect(status().isForbidden());
            for(String path:List.of("/api/v1/permission-review/runs","/api/v1/permission-review/reports/1/complete","/api/v1/permission-review/reports/not-a-number/complete"))
                mvc.perform(post(path).header("Authorization","Bearer review")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void adminReadsAndStillReceivesValidationErrorsForInvalidReport() throws Exception {
        token("ADMIN");
        when(service.reports(0, 20)).thenReturn(new ReportPage(List.of(), 0, 20, 0));
        mvc.perform(get("/api/v1/permission-review").header("Authorization", "Bearer review")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/permission-review/reports").header("Authorization", "Bearer review"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/permission-review/reports/1").header("Authorization", "Bearer review")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/permission-review/reports").header("Authorization", "Bearer review")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/permission-review/runs").header("Authorization","Bearer review")).andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/permission-review/reports/1/coverage").header("Authorization","Bearer review")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/permission-review/reports/1/export").header("Authorization","Bearer review")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/permission-review/reports/1/complete").header("Authorization","Bearer review")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/permission-review/runs/not-a-uuid").header("Authorization","Bearer review")).andExpect(status().isBadRequest());
        verify(service, never()).importReport(any(), any());
    }
}
````

## backend/src/test/java/com/duanttcsn5/library/PermissionReviewCoverageTest.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionReviewCoverageTest {
    PermissionReviewCatalog catalog;
    PermissionReviewService reviews;
    PermissionReviewRepository repository;
    UserRepository users;
    PermissionReviewCoverageService service;
    Inspection inspection;ReportDetail detail;
    final String hash="c".repeat(64);final OffsetDateTime at=OffsetDateTime.now();
    @BeforeEach void setup() throws Exception {
        catalog=new PermissionReviewCatalog(new ObjectMapper());reviews=mock(PermissionReviewService.class);
        repository=mock(PermissionReviewRepository.class);users=mock(UserRepository.class);
        service=new PermissionReviewCoverageService(reviews,repository,users);
        List<ReviewedEntry> entries=catalog.policy().entries().stream().map(e->new ReviewedEntry(e,"documented gate",
                catalog.policy().roles().stream().map(r->new RoleComparison(r.code(),e.rules().get(r.code()),"DENY".equals(e.rules().get(r.code()))?"DENY":"ALLOW",true)).toList(),true,false)).toList();
        inspection=new Inspection(catalog.policy().version(),catalog.policy().decision(),hash,at,catalog.policy().roles(),catalog.policy().modules(),entries.size(),0,0,entries,
                entries.stream().mapToInt(e->e.policy().httpMethods().size()).sum(),List.of());
        when(reviews.inspect()).thenReturn(inspection);when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(true);
        var actor=new User();actor.setId(2L);actor.setFullName("Người xác nhận");actor.setStatus("ACTIVE");var role=new Role();role.setCode("ADMIN");actor.setRole(role);when(users.findById(2L)).thenReturn(Optional.of(actor));
        full();
    }
    void full() {
        List<CaseResult> cases=new ArrayList<>();
        for(var e:catalog.policy().entries())for(String method:e.httpMethods())for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")) {
            String scenario="STANDARD";
            cases.add(result(e,method,role,scenario,true));
            if("OVERRIDE".equals(e.rules().get(role)))cases.add(result(e,method,role,"OVERRIDE",true));
            if("OWN".equals(e.rules().get(role)))cases.add(result(e,method,role,Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(e.id())?"OTHER_OWNER":"SPOOFED_OWNER",true));
        }
        set(cases,true,2,"REGRESSION");
    }
    CaseResult result(PolicyEntry e,String method,String role,String scenario,boolean passed) {
        int status=catalog.requiredStatus(e,role,scenario,200);
        var test=new EvidenceCase(e.id(),role,scenario,status,null,passed?status:500,true,at,at,"Test proof",null,method,passed);
        return new CaseResult(test,e.module(),e.label(),role.equals("ANONYMOUS")?"LOGIN_REQUIRED":e.rules().get(role),passed?"PASS":"OPEN",passed);
    }
    void set(List<CaseResult> cases,boolean current,int schema,String kind) {
        var report=new ImportReport(schema,UUID.randomUUID(),catalog.policy().version(),hash,kind,"ISOLATED_POSTGRES","unit fixture","Tester",at,at,cases.stream().map(CaseResult::test).toList());
        var summary=new ReportSummary(1L,report.runKey(),kind,"ISOLATED_POSTGRES","unit fixture","Tester","Admin",at,at,cases.size(),0,0,(int)cases.stream().filter(c->!c.passed()).count());
        detail=new ReportDetail(summary,report,cases,current);when(reviews.detail(1L)).thenReturn(detail);
    }
    @Test void fullCoverageIncludesBothPutAndPatchAndAllowsAnActiveAdminToComplete() {
        var coverage=service.coverage(1L);assertTrue(coverage.eligible());assertEquals(191,coverage.totalApis());assertEquals(764,coverage.requiredRoleCases());assertEquals(764,coverage.coveredRoleCases());assertEquals(191,coverage.coveredApis());
        service.complete(1L,2L);verify(repository).complete(1L,2L,"Người xác nhận",hash,764);
    }
    @Test void aMissingRoleOrMissingHttpMethodBlocksCompletion() {
        var cases=new ArrayList<>(detail.results());
        cases.removeIf(c->c.test().endpointId().equals("BookCopyController.update") && c.test().httpMethod().equals("PATCH") && c.test().role().equals("READER"));set(cases,true,2,"REGRESSION");
        var coverage=service.coverage(1L);assertFalse(coverage.eligible());assertEquals(763,coverage.coveredRoleCases());
        assertTrue(coverage.missing().stream().anyMatch(m->m.httpMethod().equals("PATCH") && m.role().equals("READER")));
        assertThrows(ApiException.class,()->service.complete(1L,2L));verify(repository,never()).complete(any(),any(),any(),any(),anyInt());
    }
    @Test void missingAnonymousAndMissingOwnershipEvidenceBlockCompletion() {
        var cases=detail.results().stream().filter(c->!c.test().role().equals("ANONYMOUS") && c.test().scenario().equals("STANDARD")).toList();set(cases,true,2,"REGRESSION");
        assertFalse(service.coverage(1L).eligible());assertTrue(service.coverage(1L).missing().stream().anyMatch(m->m.role().equals("ANONYMOUS")));
    }
    @Test void failedCaseStaleSourceExternalImportAndSimulationCannotBeConfirmed() {
        var cases=new ArrayList<>(detail.results());var first=cases.get(0);var e=catalog.find(first.test().endpointId()).orElseThrow();cases.set(0,result(e,first.test().httpMethod(),first.test().role(),first.test().scenario(),false));set(cases,true,2,"REGRESSION");
        assertEquals(1,service.coverage(1L).failedCases());assertFalse(service.coverage(1L).eligible());
        full();set(detail.results(),false,2,"REGRESSION");assertFalse(service.coverage(1L).eligible());
        full();when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(false);assertFalse(service.coverage(1L).eligible());
        when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(true);set(detail.results(),true,2,"SIMULATION");assertFalse(service.coverage(1L).eligible());
    }
    @Test void undocumentedApiOrMatrixDiscrepancyBlocksEvenOtherwiseCompleteEvidence() {
        when(reviews.inspect()).thenReturn(new Inspection(inspection.policyVersion(),inspection.decision(),hash,at,inspection.roles(),inspection.modules(),190,1,1,inspection.entries(),191,List.of("GET /api/v1/new-api")));
        assertFalse(service.coverage(1L).eligible());assertEquals(1,service.coverage(1L).unknownFunctions());
    }
    @Test void inactiveOrNonAdminActorCannotConfirmAndConfirmationIsIdempotent() {
        assertThrows(ApiException.class,()->service.complete(1L,null));users.findById(2L).orElseThrow().setStatus("LOCKED");assertThrows(ApiException.class,()->service.complete(1L,2L));
        users.findById(2L).orElseThrow().setStatus("ACTIVE");when(repository.completion(1L)).thenReturn(Optional.of(new Completion(1L,"Admin",at,hash)));service.complete(1L,2L);verify(repository,never()).complete(any(),any(),any(),any(),anyInt());
    }
    @Test void databaseGuardRequiresExplicitDifferentLiveDatabaseAndUniqueLoopbackTestName() {
        String name="s4092_review_"+"a".repeat(32),url="jdbc:postgresql://127.0.0.1:5433/"+name;
        assertDoesNotThrow(()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,"library_management"));
        assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,null));
        assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,name));
        for(String invalid:List.of("jdbc:postgresql://remote.example:5433/"+name,"jdbc:postgresql://127.0.0.1:5432/"+name,"jdbc:postgresql://127.0.0.1:5433/library_management","jdbc:postgresql://127.0.0.1:5433/s4092_any_name","jdbc:h2:mem:test"))
            assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(invalid,"library_management"));
    }

}
````

## backend/src/test/java/com/duanttcsn5/library/PermissionReviewInspectorTest.java

````java
package com.duanttcsn5.library;

import com.duanttcsn5.library.service.PermissionReviewInspector;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

class PermissionReviewInspectorTest {
    static class Excess {
        static class FeePaymentController {
            @PreAuthorize("hasAnyRole('LIBRARIAN','READER')")
            public void collect() {}
        }
    }
    static class Missing {
        static class FeePaymentController {
            @PreAuthorize("hasRole('ADMIN')")
            public void collect() {}
        }
    }
    void detects(Object replacement, String role, String observed) throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(CriticalPermissionApiTest.Config.class); context.refresh();
            var inspector = context.getBean(PermissionReviewInspector.class);
            String oldFingerprint = inspector.inspect().fingerprint();
            var mappings = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            var original = mappings.getHandlerMethods().keySet().stream()
                    .filter(value -> value.getPatternValues().contains("/api/v1/fee-payments/readers/{readerId}/collect-full"))
                    .findFirst().orElseThrow();
            mappings.unregisterMapping(original);
            mappings.registerMapping(original, replacement, replacement.getClass().getMethod("collect"));
            var snapshot = inspector.inspect();
            var row = snapshot.entries().stream().filter(value -> value.policy().id().equals("FeePaymentController.collect")).findFirst().orElseThrow();
            assertTrue(row.mapped()); assertTrue(row.different());
            var comparison = row.roles().stream().filter(value -> value.role().equals(role)).findFirst().orElseThrow();
            assertEquals(observed, comparison.actualGate()); assertFalse(comparison.matches());
            assertNotEquals(oldFingerprint, snapshot.fingerprint());
        }
    }
    @Test void detectsAnExcessReaderPermission() throws Exception { detects(new Excess.FeePaymentController(), "READER", "ALLOW"); }
    @Test void detectsAMissingLibrarianPermission() throws Exception { detects(new Missing.FeePaymentController(), "LIBRARIAN", "DENY"); }
    static class UndocumentedApi { public void probe() {} }
    @Test void discoversAnUndocumentedRuntimeApiAndMarksEveryRoleUnknown() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            var servlet=new MockServletContext();context.setServletContext(servlet);context.register(CriticalPermissionApiTest.Config.class);context.refresh();
            var mappings=context.getBean("requestMappingHandlerMapping",RequestMappingHandlerMapping.class);
            var mapping=org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths("/api/v1/s4092-undocumented")
                    .methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
            mappings.registerMapping(mapping,new UndocumentedApi(),UndocumentedApi.class.getMethod("probe"));
            var inspector=new PermissionReviewInspector(new com.duanttcsn5.library.service.PermissionReviewCatalog(new tools.jackson.databind.ObjectMapper()),mappings,
                    context.getBean(org.springframework.security.web.SecurityFilterChain.class),servlet);
            var result=inspector.inspect();
            assertTrue(result.undocumentedApis().contains("GET /api/v1/s4092-undocumented"));
            var row=result.entries().stream().filter(e->e.policy().path().equals("/api/v1/s4092-undocumented")).findFirst().orElseThrow();
            assertTrue(row.different());assertEquals(4,row.roles().size());
            assertTrue(row.roles().stream().allMatch(r->r.documented().equals("UNKNOWN") && !r.matches()));
            assertEquals(192,result.totalApis());assertTrue(result.unresolvedFunctions()>0);
        }
    }

}
````

## frontend/src/features/s4-09-permission-review/PermissionReviewPage.tsx

````tsx
import { useEffect, useRef, useState } from 'react'
import { Download, FileCheck2, Play, RefreshCw, ShieldCheck, Upload, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import Modal from '../../components/ui/Modal'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import {
  comparisonStatus, downloadReport, formatReviewTime, gateLabels, parseReportFile, permissionReviewService, ruleLabels, selectEntries,
  type Coverage, type RunState, type ImportReport, type Inspection, type ReportDetail, type ReportPage, type ReviewedEntry,
} from './permissionReviewService'

export default function PermissionReviewPage() {
  if (getCurrentUser()?.role !== 'ADMIN') {
    return <Card className="p-6"><h1 className="font-semibold text-slate-900">Không có quyền truy cập</h1>
      <p className="mt-2 text-sm text-slate-600">Chỉ Quản trị hệ thống được xem bảng đối chiếu và biên bản rà soát phân quyền.</p></Card>
  }
  return <PermissionReviewWorkspace />
}

export function PermissionReviewWorkspace() {
  const [data, setData] = useState<Inspection | null>(null)
  const [reports, setReports] = useState<ReportPage | null>(null)
  const [loading, setLoading] = useState(true), [reportsLoading, setReportsLoading] = useState(true)
  const [revision, setRevision] = useState(0), [reportPage, setReportPage] = useState(0), [page, setPage] = useState(1)
  const [module, setModule] = useState(''), [query, setQuery] = useState(''), [differences, setDifferences] = useState(false)
  const [role, setRole] = useState(''), [comparison, setComparison] = useState('')
  const [run, setRun] = useState<RunState | null>(null), [starting, setStarting] = useState(false)
  const [coverage, setCoverage] = useState<Coverage | null>(null), [coverageError, setCoverageError] = useState('')
  const [confirming, setConfirming] = useState(false), [confirmOpen, setConfirmOpen] = useState(false), [exporting, setExporting] = useState(false)
  const [resultPage, setResultPage] = useState(1), [resultRole, setResultRole] = useState(''), [resultOutcome, setResultOutcome] = useState('')
  const startLock = useRef(false), confirmLock = useRef(false)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error' | 'warning'; message: string } | null>(null)
  const [report, setReport] = useState<ReportDetail | null>(null), [opening, setOpening] = useState<number | null>(null)
  const [file, setFile] = useState<ImportReport | null>(null), [filename, setFilename] = useState(''), [fileError, setFileError] = useState('')
  const [importing, setImporting] = useState(false)
  const importLock = useRef(false), fileVersion = useRef(0), alive = useRef(true)
  const detailController = useRef<AbortController | null>(null)
  useEffect(() => { alive.current = true; return () => { alive.current = false; detailController.current?.abort(); fileVersion.current++ } }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    permissionReviewService.inspect(controller.signal)
      .then(value => { if (!controller.signal.aborted) setData(value) })
      .catch(error => { if (!controller.signal.aborted) { setData(null); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được bảng đối chiếu phân quyền.') }) } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [revision])
  useEffect(() => {
    const controller = new AbortController()
    setReportsLoading(true)
    permissionReviewService.reports(reportPage, controller.signal)
      .then(value => { if (!controller.signal.aborted) setReports(value) })
      .catch(error => { if (!controller.signal.aborted) { setReports(null); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được danh sách biên bản.') }) } })
      .finally(() => { if (!controller.signal.aborted) setReportsLoading(false) })
    return () => controller.abort()
  }, [revision, reportPage])

  useEffect(() => {
    const controller = new AbortController()
    permissionReviewService.latestRun(controller.signal).then(value => { if (!controller.signal.aborted) setRun(value) })
      .catch(error => { if (!controller.signal.aborted) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được trạng thái kiểm thử. Bấm Làm mới để thử lại.') }) })
    return () => controller.abort()
  }, [revision])
  useEffect(() => {
    if (!run || !['STARTING', 'RUNNING'].includes(run.state)) return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      permissionReviewService.runStatus(run.runKey, controller.signal).then(value => {
        if (controller.signal.aborted) return
        setRun(value)
        if (!['STARTING', 'RUNNING'].includes(value.state)) {
          setRevision(current => current + 1)
          setFeedback({ tone: value.state === 'SUCCEEDED' ? 'success' : 'error', message: value.message })
        }
      }).catch(error => { if (!controller.signal.aborted) { window.clearInterval(timer); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Mất kết nối theo dõi kiểm thử. Bấm Làm mới để xem lại; tác vụ vẫn có thể đang chạy.') }) } })
    }, 2000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [run?.runKey, run?.state])
  useEffect(() => {
    setCoverage(null); setCoverageError(''); setConfirmOpen(false); setResultPage(1); setResultRole(''); setResultOutcome('')
    if (!report) return
    const controller = new AbortController()
    permissionReviewService.coverage(report.summary.id, controller.signal).then(value => { if (!controller.signal.aborted) setCoverage(value) })
      .catch(error => { if (!controller.signal.aborted) setCoverageError(getApiErrorMessage(error, 'Không tải được điều kiện hoàn tất. Đóng và mở lại biên bản để thử lại.')) })
    return () => controller.abort()
  }, [report?.summary.id])
  async function startRun() {
    if (startLock.current) return
    startLock.current = true; setStarting(true); setFeedback(null)
    try { const value = await permissionReviewService.startRun(); if (alive.current) { setRun(value); setFeedback({ tone: 'success', message: 'Đã bắt đầu kiểm thử trên database riêng. Bạn có thể theo dõi tiến độ bên dưới.' }) } }
    catch (error) { if (alive.current) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không bắt đầu được kiểm thử toàn bộ.') }) }
    finally { startLock.current = false; if (alive.current) setStarting(false) }
  }
  async function confirmCompletion() {
    if (!report || !coverage?.eligible || confirmLock.current) return
    confirmLock.current = true; setConfirming(true); setFeedback(null)
    try { const value = await permissionReviewService.complete(report.summary.id); if (alive.current) { setCoverage(value); setConfirmOpen(false); setFeedback({ tone: 'success', message: 'Đã xác nhận hoàn tất rà soát phân quyền toàn hệ thống.' }) } }
    catch (error) { if (alive.current) { setConfirmOpen(false); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Chưa thể xác nhận hoàn tất. Hãy mở lại biên bản để xem điều kiện hiện tại.') }) } }
    finally { confirmLock.current = false; if (alive.current) setConfirming(false) }
  }
  async function exportReport() {
    if (!report || exporting) return
    setExporting(true)
    try { const exported = await permissionReviewService.exportReport(report.summary.id); if (alive.current) { downloadReport(report, exported); setFeedback({ tone: 'success', message: 'Đã xuất biên bản đầy đủ cùng lịch sử rà soát trong sprint.' }) } }
    catch (error) { if (alive.current) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không xuất được biên bản. Vui lòng thử lại.') }) }
    finally { if (alive.current) setExporting(false) }
  }
  async function chooseFile(selected?: File) {
    const version = ++fileVersion.current
    setFile(null); setFilename(selected?.name ?? ''); setFileError('')
    if (!selected) return
    try {
      if (selected.size > 1024 * 1024) throw new Error('Biên bản tối đa 1 MB. Chọn tệp JSON kết quả của bộ kiểm thử phân quyền.')
      const parsed = parseReportFile(await selected.text())
      if (alive.current && version === fileVersion.current) setFile(parsed)
    } catch (error) {
      if (!alive.current || version !== fileVersion.current) return
      const message = error instanceof Error ? error.message : 'Không đọc được tệp biên bản.'
      setFileError(message); setFeedback({ tone: 'warning', message })
    }
  }
  async function importFile() {
    if (!file || importLock.current) return
    importLock.current = true; setImporting(true); setFeedback(null)
    try {
      const saved = await permissionReviewService.importReport(file)
      if (!alive.current) return
      setReport(saved); setFile(null); setFilename(''); setFileError(''); setReportPage(0); setRevision(value => value + 1)
      setFeedback({ tone: 'success', message: 'Đã lưu biên bản #' + saved.summary.id + '. Kết quả được phân loại theo bằng chứng kiểm thử.' })
    } catch (error) {
      if (alive.current) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không lưu được biên bản rà soát.') })
    } finally { importLock.current = false; if (alive.current) setImporting(false) }
  }
  async function openReport(id: number) {
    detailController.current?.abort()
    setFeedback(null)
    const controller = new AbortController(); detailController.current = controller
    setOpening(id)
    try { const value = await permissionReviewService.detail(id, controller.signal); if (!controller.signal.aborted) setReport(value) }
    catch (error) { if (!controller.signal.aborted) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được chi tiết biên bản.') }) }
    finally { if (!controller.signal.aborted) setOpening(null) }
  }
  const entries = data ? selectEntries(data, module, query, differences, role, comparison) : []
  const pageSize = 10, totalPages = Math.max(1, Math.ceil(entries.length / pageSize)), currentPage = Math.min(page, totalPages)
  function resetFilter() { setPage(1) }
  const visibleResults = report?.results.filter(item => (!resultRole || item.test.role === resultRole) && (!resultOutcome || item.outcome === resultOutcome)) ?? []
  const resultPages = Math.max(1, Math.ceil(visibleResults.length / 20))
  const resultCurrent = Math.min(resultPage, resultPages)
  const running = Boolean(run && ['STARTING', 'RUNNING'].includes(run.state))
  const notify = feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />
  function closeReport() { setReport(null); setFeedback(null) }
  const paginationClass = 'px-3 sm:px-5 [&>div]:flex-wrap [&>div>span]:min-w-0'
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Rà soát phân quyền" description="Đối chiếu toàn bộ API, kiểm thử bốn vai trò và xác nhận kết quả rà soát."
      action={<Button type="button" variant="secondary" disabled={loading || importing} onClick={() => setRevision(value => value + 1)}><RefreshCw size={16} />Làm mới</Button>} />
    {!report && notify}
    {loading ? <LoadingState /> : !data ? <Card className="p-6"><EmptyState title="Chưa tải được bảng phân quyền" description="Bấm Làm mới để thử lại khi kết nối máy chủ hoạt động." /></Card> : <>
      <div className="grid gap-3 sm:grid-cols-3">
        <Metric label="Tổng API nghiệp vụ" value={data.totalApis ?? data.reviewedFunctions} />
        <Metric label="Thao tác lệch cổng quyền" value={data.differentFunctions} warning={data.differentFunctions > 0} />
        <Metric label="Thao tác chưa xác định" value={data.unresolvedFunctions} warning={data.unresolvedFunctions > 0} />
      </div>
      <Card className="space-y-3 p-4 sm:p-5">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><ShieldCheck size={19} />Căn cứ đối chiếu</h2>
        <p className="text-sm leading-6 text-slate-600">{data.decision} Phiên bản {data.policyVersion}. Đối chiếu lúc {formatReviewTime(data.inspectedAt)}.</p>
        <p className="text-sm leading-6 text-slate-600">Quyền thực tế bên dưới là cổng kiểm tra vai trò của API đang chạy. Quyền theo dữ liệu cá nhân và luồng ngoại lệ còn được kiểm tra ở tầng nghiệp vụ; xem bằng chứng trong biên bản kiểm thử để xác nhận các điều kiện này.</p>
        <details className="text-sm"><summary className="cursor-pointer font-medium text-slate-700">Xem dấu vân tay bản kiểm thử</summary><code className="mt-2 block break-all rounded-lg bg-slate-50 p-3 text-xs">{data.fingerprint}</code></details>
      </Card>
      <Card className="space-y-4 p-4 sm:p-5">
        <div className="grid items-end gap-3 md:grid-cols-2">
          <label className="text-sm font-medium text-slate-700">Phân hệ
            <select value={module} onChange={event => { setModule(event.target.value); resetFilter() }} className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3">
              <option value="">Tất cả phân hệ</option>{data.modules.map(item => <option key={item.code} value={item.code}>{item.priority}. {item.name}</option>)}
            </select></label>
          <label className="text-sm font-medium text-slate-700">Tìm chức năng hoặc API
            <input value={query} maxLength={150} onChange={event => { setQuery(event.target.value); resetFilter() }} placeholder="Ví dụ: thu phí, collect-full" className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 px-3" /></label>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-medium text-slate-700">Vai trò<select aria-label="Vai trò" value={role} onChange={event => { setRole(event.target.value); resetFilter() }} className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3"><option value="">Cả bốn vai trò</option>{data.roles.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select></label>
          <label className="text-sm font-medium text-slate-700">Trạng thái đối chiếu<select aria-label="Trạng thái đối chiếu" value={comparison} onChange={event => { setComparison(event.target.value); resetFilter() }} className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3"><option value="">Tất cả trạng thái</option><option value="MATCH">Khớp</option><option value="DIFFERENT">Sai lệch</option><option value="UNKNOWN">Chưa xác định</option></select></label>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm"><label className="flex min-h-11 items-center gap-2 text-slate-700"><input type="checkbox" checked={differences} onChange={event => { setDifferences(event.target.checked); resetFilter() }} />Chỉ hiện sai lệch hoặc chưa xác định</label>
          <span role="status" className="text-slate-500">{entries.length} / {data.reviewedFunctions} chức năng khớp bộ lọc</span></div>
      </Card>
      {entries.length ? <div className="space-y-4">{entries.slice((currentPage - 1) * pageSize, currentPage * pageSize).map(entry => <ComparisonCard key={entry.policy.id} entry={entry} data={data} roleFilter={role} />)}
        <TablePagination className={paginationClass} page={currentPage} totalItems={entries.length} totalPages={totalPages} pageSize={pageSize} onPageChange={setPage} /></div>
        : <Card className="p-5"><EmptyState title={differences ? 'Không có sai lệch cổng quyền theo bộ lọc' : 'Không có chức năng phù hợp'} description="Thay đổi phân hệ hoặc từ khóa để xem các thao tác khác. Kết quả này không thay thế kiểm thử API và kiểm thử sở hữu dữ liệu." /></Card>}
    </>}
    <Card className="space-y-4 p-4 sm:p-5">
      <h2 className="font-semibold text-slate-900">Kiểm thử phân quyền toàn hệ thống</h2>
      <p className="text-sm leading-6 text-slate-600">Kiểm tra từng API bằng bốn vai trò và người chưa đăng nhập trên database thử riêng. Mỗi lần chạy dùng database tạm riêng và xóa database thử sau khi lưu kết quả.</p>
      <Button type="button" onClick={() => void startRun()} disabled={starting || running || !data} loading={starting}><Play size={17} />Chạy kiểm thử toàn bộ</Button>
      {run && <div className={'space-y-2 rounded-xl border p-4 text-sm ' + (run.state === 'FAILED' ? 'border-red-200 bg-red-50' : 'border-slate-200 bg-slate-50')} aria-live="polite">
        <p className="font-semibold">{run.state === 'STARTING' ? 'Đang chuẩn bị' : run.state === 'RUNNING' ? 'Đang kiểm thử' : run.state === 'SUCCEEDED' ? 'Đã chạy xong' : 'Chưa hoàn tất kiểm thử'}</p>
        <p className="leading-6">{run.message}</p><p className="text-xs text-slate-500">Yêu cầu bởi {run.requestedBy} · {formatReviewTime(run.startedAt)}</p>
        {run.reportId && <Button type="button" variant="secondary" loading={opening === run.reportId} disabled={opening !== null} onClick={() => void openReport(run.reportId!)}>Xem kết quả kiểm thử</Button>}
      </div>}
    </Card>
    <Card className="space-y-4 p-4 sm:p-5">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Upload size={18} />Nhập kết quả kiểm thử</h2>
      <p className="text-sm leading-6 text-slate-600">Chọn tệp JSON do bộ kiểm thử riêng tạo ra. Thao tác này chỉ lưu bằng chứng và biên bản, không chạy giao dịch mượn, trả hoặc thu tiền trên hệ thống đang dùng. Biên bản mô phỏng luôn được ghi rõ.</p>
      <label className="block text-sm font-medium text-slate-700">Tệp kết quả JSON (tối đa 1 MB)
        <span className="mt-2 flex min-h-11 min-w-0 flex-wrap items-center gap-3 rounded-xl border border-slate-300 p-2 focus-within:ring-4 focus-within:ring-blue-100">
          <span className="rounded-lg bg-slate-100 px-3 py-2 text-sm font-semibold">Chọn tệp JSON</span>
          <span className="min-w-0 break-all text-sm font-normal text-slate-500">{filename || 'Chưa chọn tệp'}</span>
          <input type="file" accept=".json,application/json" disabled={importing} aria-invalid={Boolean(fileError)} aria-describedby={fileError ? 'review-file-error' : undefined}
            onChange={event => { const selected = event.target.files?.[0]; event.currentTarget.value = ''; void chooseFile(selected) }} className="sr-only" />
        </span></label>
      {fileError && <p id="review-file-error" role="alert" className="break-words text-sm text-red-700">{fileError}</p>}
      {file && <p className="break-words text-sm text-slate-600">{filename} · {file.kind === 'SIMULATION' ? 'Mô phỏng riêng' : 'Kiểm thử hồi quy'} · {file.checks.length} trường hợp</p>}
      <Button type="button" disabled={!file || importing || !data} loading={importing} onClick={() => void importFile()}><FileCheck2 size={17} />Lưu biên bản</Button>
    </Card>
    <Card className="overflow-hidden">
      <div className="border-b border-slate-200 p-4 sm:p-5"><h2 className="font-semibold text-slate-900">Biên bản rà soát đã lưu</h2><p className="mt-1 text-sm text-slate-500">Số sai lệch và số đã khắc phục được tính riêng trong từng biên bản.</p></div>
      {reportsLoading ? <LoadingState /> : !reports ? <p className="p-5 text-sm text-slate-600">Chưa tải được biên bản. Bấm Làm mới để thử lại.</p> : !reports.items.length ? <div className="p-5"><EmptyState title="Chưa có biên bản" description="Bấm Chạy kiểm thử toàn bộ để tạo biên bản; hoặc nhập kết quả từ bộ kiểm thử riêng." /></div> : <>
        <div className="divide-y divide-slate-200">{reports.items.map(item => <article key={item.id} className="space-y-3 p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold text-slate-900">Biên bản #{item.id} <span className={'ml-2 rounded-lg px-2 py-1 text-xs ' + (item.kind === 'SIMULATION' ? 'bg-amber-50 text-amber-800' : 'bg-blue-50 text-blue-800')}>{item.kind === 'SIMULATION' ? 'Mô phỏng riêng' : 'Hồi quy'}</span></h3>
            <Button type="button" variant="secondary" size="sm" loading={opening === item.id} disabled={opening !== null} onClick={() => void openReport(item.id)}>Xem biên bản</Button></div>
          <p className="break-words text-sm text-slate-600">{item.caseCount} trường hợp · {item.detectedCount} sai lệch · {item.fixedCount} đã khắc phục · {item.failedCount} chưa đạt</p>
          <p className="break-words text-xs leading-5 text-slate-500">Chạy bởi {item.executedBy} · {formatReviewTime(item.completedAt)}. Nhập bởi {item.importedBy} · {formatReviewTime(item.importedAt)}. Môi trường: {item.environment}.</p>
        </article>)}</div>
        <TablePagination className={paginationClass} page={reportPage + 1} totalItems={reports.totalItems} totalPages={Math.max(1, Math.ceil(reports.totalItems / reports.size))} pageSize={reports.size} onPageChange={value => setReportPage(value - 1)} />
      </>}
    </Card>
    {report && <Modal label={'Biên bản rà soát #' + report.summary.id} onClose={closeReport}>
      <section className="max-h-[85dvh] w-[min(960px,calc(100vw-32px))] overflow-y-auto rounded-2xl bg-white p-4 sm:p-6">
        <div className="flex items-start justify-between gap-3"><h2 className="text-lg font-semibold text-slate-900">Biên bản rà soát #{report.summary.id}</h2><Button type="button" variant="ghost" size="sm" onClick={closeReport} aria-label="Đóng biên bản"><X size={18} /></Button></div>
        <div className="mt-4 space-y-4">
          {notify}
          <p className="rounded-xl bg-slate-50 p-3 text-sm leading-6 text-slate-700">{report.summary.kind === 'SIMULATION' ? 'MÔ PHỎNG: sai lệch được tạo có chủ đích trong môi trường kiểm thử riêng, không phải kết luận lỗi thật của hệ thống.' : coverage?.serverExecuted ? 'Biên bản do bộ chạy toàn hệ thống tạo từ database kiểm thử riêng.' : 'Biên bản nhập từ bên ngoài; chỉ dùng đối chiếu bằng chứng, chưa đủ để xác nhận hoàn tất toàn hệ thống.'}</p>
          {!report.currentFingerprint && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">Biên bản thuộc bản code khác. Cần chạy lại để xác nhận trạng thái hiện tại.</p>}
          <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Người chạy kiểm thử</dt><dd className="break-words font-medium">{report.summary.executedBy}</dd></div><div><dt className="text-slate-500">Nguồn kiểm thử</dt><dd className="break-words font-medium">{report.summary.sourceReference}</dd></div><div><dt className="text-slate-500">Người nhập biên bản</dt><dd className="break-words font-medium">{report.summary.importedBy}</dd></div><div><dt className="text-slate-500">Thời điểm nhập</dt><dd>{formatReviewTime(report.summary.importedAt)}</dd></div></dl>
          <div className="grid gap-3 sm:grid-cols-3"><Metric label="Sai lệch phát hiện" value={report.summary.detectedCount} /><Metric label="Đã khắc phục" value={report.summary.fixedCount} /><Metric label="Chưa đạt" value={report.summary.failedCount} warning={report.summary.failedCount > 0} /></div>
          {coverageError && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{coverageError}</p>}
          {coverage && <div className="space-y-3 rounded-xl border border-slate-200 p-4">
            <div className="grid gap-3 sm:grid-cols-2"><Metric label="API đã kiểm thử / tổng API" value={coverage.coveredApis} /><Metric label={'Trường hợp vai trò / ' + coverage.requiredRoleCases} value={coverage.coveredRoleCases} /></div>
            <p className="text-sm">{coverage.coveredApis} / {coverage.totalApis} API · {coverage.passedCases} đạt · {coverage.failedCases} chưa đạt · {coverage.unresolvedFindings} sai lệch chưa xử lý</p>
            {coverage.reasons.map(reason => <p key={reason} className="text-sm leading-6 text-red-700">{reason}</p>)}
            {coverage.missing.length > 0 && <details className="text-sm"><summary className="cursor-pointer font-medium">Xem {coverage.missing.length} trường hợp còn thiếu</summary><ul className="mt-2 max-h-48 space-y-2 overflow-y-auto">{coverage.missing.map(item => <li className="break-all" key={item.endpointId + item.httpMethod + item.role + item.scenario}>{item.httpMethod} {item.path} · {data?.roles.find(role => role.code === item.role)?.name ?? item.role} · {item.scenario}</li>)}</ul></details>}
            {coverage.completion ? <p className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">Đã xác nhận bởi {coverage.completion.confirmedBy} · {formatReviewTime(coverage.completion.confirmedAt)}{!coverage.eligible && ' — cần kiểm thử lại cho trạng thái hiện tại.'}</p> : <Button type="button" disabled={!coverage.eligible || confirming} onClick={() => setConfirmOpen(true)}><ShieldCheck size={17} />Xác nhận hoàn tất rà soát</Button>}
            {confirmOpen && <div role="group" aria-label="Xác nhận hoàn tất" className="space-y-3 rounded-lg bg-blue-50 p-3 text-sm"><p>Bạn xác nhận hoàn tất rà soát toàn hệ thống theo biên bản này? Hệ thống sẽ kiểm tra lại điều kiện trước khi lưu người xác nhận và thời điểm.</p><div className="flex flex-wrap gap-2"><Button type="button" loading={confirming} disabled={confirming} onClick={() => void confirmCompletion()}>Đồng ý xác nhận</Button><Button type="button" variant="secondary" disabled={confirming} onClick={() => setConfirmOpen(false)}>Hủy</Button></div></div>}
          </div>}
          <Button type="button" variant="secondary" loading={exporting} disabled={exporting} onClick={() => void exportReport()}><Download size={16} />Xuất biên bản đầy đủ</Button>
          <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Vai trò trong kết quả<select aria-label="Vai trò trong kết quả" value={resultRole} onChange={event => { setResultRole(event.target.value); setResultPage(1) }} className="mt-1 min-h-11 w-full rounded-xl border border-slate-300 px-3"><option value="">Tất cả vai trò</option>{data?.roles.map(role => <option key={role.code} value={role.code}>{role.name}</option>)}<option value="ANONYMOUS">Chưa đăng nhập</option></select></label><label className="text-sm">Kết quả kiểm thử<select aria-label="Kết quả kiểm thử" value={resultOutcome} onChange={event => { setResultOutcome(event.target.value); setResultPage(1) }} className="mt-1 min-h-11 w-full rounded-xl border border-slate-300 px-3"><option value="">Tất cả kết quả</option><option value="PASS">Đạt</option><option value="FIXED">Đã khắc phục</option><option value="OPEN">Chưa đạt</option></select></label></div>
          <p className="text-sm text-slate-500">{visibleResults.length} trường hợp khớp bộ lọc</p>
          {visibleResults.slice((resultCurrent - 1) * 20, resultCurrent * 20).map(result => <article key={result.test.endpointId + result.test.httpMethod + result.test.role + result.test.scenario} className="space-y-2 rounded-xl border border-slate-200 p-4 text-sm">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold text-slate-900">{result.functionName}</h3><span className={'rounded-lg px-2 py-1 text-xs font-semibold ' + (result.passed ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700')}>{result.outcome === 'FIXED' ? 'Đã khắc phục' : result.passed ? 'Đạt' : 'Chưa đạt'}</span></div>
            <p className="break-words text-slate-600">{data?.modules.find(item => item.code === result.module)?.name ?? result.module} · {data?.roles.find(item => item.code === result.test.role)?.name ?? 'Chưa đăng nhập'} · {result.test.scenario}</p>
            <code className="block break-all text-xs">{result.test.httpMethod} {data?.entries.find(entry => entry.policy.id === result.test.endpointId)?.policy.path ?? result.test.endpointId}</code><p>Theo tài liệu: {ruleLabels[result.documented]} · HTTP mong đợi: {result.test.expectedStatus}</p>
            <p>Trước: {result.test.beforeStatus ?? 'Không có sai lệch trước đó'} → Kiểm thử lại: <strong>{result.test.actualStatus}</strong></p>
            <p>Dữ liệu sau yêu cầu bị từ chối: {result.test.resourceUnchanged === true ? 'Không thay đổi' : result.test.resourceUnchanged === false ? 'Có thay đổi' : 'Không áp dụng / chưa kiểm tra'}</p>
            <p className="break-words leading-6 text-slate-600">Bằng chứng: {result.test.evidence}</p>
            {result.test.correction && <p className="break-words leading-6">Nội dung sửa: {result.test.correction}</p>}
            <p className="text-xs leading-5 text-slate-500">Phát hiện: {formatReviewTime(result.test.detectedAt)} · Kiểm thử lại: {formatReviewTime(result.test.testedAt)}</p>
          </article>)}
          <TablePagination className={paginationClass} page={resultCurrent} totalItems={visibleResults.length} totalPages={resultPages} pageSize={20} onPageChange={setResultPage} />
        </div>
      </section>
    </Modal>}
  </div>
}

function Metric({ label, value, warning = false }: { label: string; value: number; warning?: boolean }) {
  return <Card className="p-4"><p className="text-sm text-slate-500">{label}</p><p className={'mt-2 text-2xl font-semibold ' + (warning ? 'text-red-700' : 'text-slate-900')}>{value.toLocaleString('vi-VN')}</p></Card>
}
function ComparisonCard({ entry, data, roleFilter }: { entry: ReviewedEntry; data: Inspection; roleFilter: string }) {
  return <Card className="min-w-0 space-y-4 p-4 sm:p-5">
    <div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="text-xs font-medium text-slate-500">{data.modules.find(item => item.code === entry.policy.module)?.name}</p><h3 className="mt-1 break-words font-semibold text-slate-900">{entry.policy.label}</h3></div>
      <span className={'rounded-lg px-2.5 py-1 text-xs font-semibold ' + (entry.different ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-800')}>{entry.different ? 'Cần kiểm tra' : 'Khớp cổng quyền'}</span></div>
    <code className="block break-all rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">{entry.policy.httpMethods.join(' / ')} {entry.policy.path}</code>
    <div className="grid min-w-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">{entry.roles.filter(role => !roleFilter || role.role === roleFilter).map(role => <div key={role.role} className={'min-w-0 rounded-xl border p-3 ' + (role.matches ? 'border-slate-200' : 'border-red-200 bg-red-50')}>
      <p className="break-words text-sm font-semibold text-slate-900">{data.roles.find(item => item.code === role.role)?.name}</p>
      <p className="mt-2 text-xs font-medium">{comparisonStatus(role) === 'UNKNOWN' ? 'Chưa xác định' : role.matches ? 'Khớp' : 'Sai lệch'}</p><dl className="mt-3 space-y-2 text-xs leading-5"><div><dt className="text-slate-500">Theo tài liệu</dt><dd className="font-medium text-slate-800">{ruleLabels[role.documented]}</dd></div><div><dt className="text-slate-500">Cổng API thực tế</dt><dd className={role.matches ? 'text-slate-700' : 'font-semibold text-red-700'}>{gateLabels[role.actualGate]}</dd></div></dl>
    </div>)}</div>
    {entry.policy.condition && <p className="text-sm leading-6 text-slate-600">Điều kiện: {entry.policy.condition}</p>}
    {Object.values(entry.policy.rules).includes('OWN') && <p className="text-xs leading-5 text-slate-500">Dữ liệu cá nhân được xác định từ tài khoản đăng nhập; không lấy danh tính từ tham số do trình duyệt tự gửi.</p>}
    <details className="text-xs text-slate-500"><summary className="cursor-pointer">Xem căn cứ trong source</summary><p className="mt-2 break-all">{entry.policy.controller}.{entry.policy.handler}</p><code className="mt-1 block break-all">{entry.guard}</code></details>
  </Card>
}
````

## frontend/src/features/s4-09-permission-review/permissionReviewService.ts

````typescript
import { apiClient } from '../../core/api/apiClient'

export type Rule = 'ALLOW' | 'DENY' | 'OWN' | 'OVERRIDE' | 'PUBLIC' | 'IMMUTABLE' | 'UNKNOWN'
export interface RoleOption { code: string; name: string }
export interface ModuleOption { code: string; name: string; priority: number }
export interface PolicyEntry {
  id: string; module: string; label: string; controller: string; handler: string
  path: string; httpMethods: string[]; rules: Record<string, Rule>; condition: string; mutating: boolean
}
export interface ReviewedEntry {
  policy: PolicyEntry; guard: string; mapped: boolean; different: boolean
  roles: { role: string; documented: Rule; actualGate: 'ALLOW' | 'DENY' | 'UNKNOWN'; matches: boolean }[]
}
export interface Inspection {
  policyVersion: string; decision: string; fingerprint: string; inspectedAt: string
  roles: RoleOption[]; modules: ModuleOption[]; reviewedFunctions: number
  differentFunctions: number; unresolvedFunctions: number; totalApis: number; undocumentedApis: string[]; entries: ReviewedEntry[]
}
export interface EvidenceCase {
  endpointId: string; role: string; scenario: 'STANDARD' | 'OVERRIDE' | 'OTHER_OWNER' | 'SPOOFED_OWNER'
  expectedStatus: number; beforeStatus: number | null; actualStatus: number
  httpMethod?: string | null; assertionsPassed?: boolean | null; resourceUnchanged: boolean | null; detectedAt: string; testedAt: string; evidence: string; correction: string | null
}
export interface ImportReport {
  schemaVersion: 1 | 2; runKey: string; policyVersion: string; fingerprint: string
  kind: 'REGRESSION' | 'SIMULATION'; environment: 'MOCKMVC' | 'ISOLATED_POSTGRES'
  sourceReference: string; executedBy: string; startedAt: string; completedAt: string; checks: EvidenceCase[]
}
export interface ReportSummary {
  id: number; runKey: string; kind: ImportReport['kind']; environment: ImportReport['environment']
  sourceReference: string; executedBy: string; importedBy: string; importedAt: string; completedAt: string
  caseCount: number; detectedCount: number; fixedCount: number; failedCount: number
}
export interface CaseResult {
  test: EvidenceCase; module: string; functionName: string; documented: Rule | 'LOGIN_REQUIRED'
  outcome: 'PASS' | 'FIXED' | 'OPEN'; passed: boolean
}
export interface ReportDetail {
  summary: ReportSummary; evidence: ImportReport; results: CaseResult[]; currentFingerprint: boolean
}
export interface ReportPage { items: ReportSummary[]; page: number; size: number; totalItems: number }
export interface RunState { runKey: string; state: 'STARTING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; message: string; requestedBy: string; startedAt: string; finishedAt: string | null; reportId: number | null }
export interface Coverage {
  totalApis: number; coveredApis: number; requiredRoleCases: number; coveredRoleCases: number; passedCases: number; failedCases: number
  unresolvedFindings: number; matrixDifferences: number; unknownFunctions: number; currentSource: boolean; serverExecuted: boolean; eligible: boolean
  missing: { endpointId: string; httpMethod: string; path: string; role: string; scenario: string }[]; reasons: string[]
  completion: { reportId: number; confirmedBy: string; confirmedAt: string; fingerprint: string } | null
}
const base = '/permission-review'
export const permissionReviewService = {
  async startRun() { return (await apiClient.post<RunState>(base + '/runs')).data },
  async latestRun(signal?: AbortSignal) { return (await apiClient.get<RunState | null>(base + '/runs/latest', { signal })).data },
  async runStatus(key: string, signal?: AbortSignal) { return (await apiClient.get<RunState>(base + '/runs/' + encodeURIComponent(key), { signal })).data },
  async coverage(id: number, signal?: AbortSignal) { return (await apiClient.get<Coverage>(base + '/reports/' + id + '/coverage', { signal })).data },
  async complete(id: number) { return (await apiClient.post<Coverage>(base + '/reports/' + id + '/complete')).data },
  async exportReport(id: number) { return (await apiClient.get(base + '/reports/' + id + '/export')).data as unknown },
  async inspect(signal?: AbortSignal) { return (await apiClient.get<Inspection>(base, { signal })).data },
  async reports(page: number, signal?: AbortSignal) {
    return (await apiClient.get<ReportPage>(base + '/reports', { params: { page, size: 10 }, signal })).data
  },
  async detail(id: number, signal?: AbortSignal) { return (await apiClient.get<ReportDetail>(base + '/reports/' + id, { signal })).data },
  async importReport(report: ImportReport) { return (await apiClient.post<ReportDetail>(base + '/reports', report)).data },
}
export const ruleLabels: Record<Rule | 'LOGIN_REQUIRED', string> = {
  ALLOW: 'Được phép', DENY: 'Không được phép', OWN: 'Chỉ dữ liệu cá nhân',
  OVERRIDE: 'Ngoại lệ có lý do', PUBLIC: 'Công khai', IMMUTABLE: 'Cấm sửa/xóa (405)', UNKNOWN: 'Chưa xác định', LOGIN_REQUIRED: 'Phải đăng nhập',
}
export const gateLabels = { ALLOW: 'Cho qua cổng vai trò', DENY: 'Bị chặn tại cổng vai trò', UNKNOWN: 'Chưa xác định được' }
export function selectEntries(data: Inspection, module: string, query: string, differencesOnly: boolean, role = '', status = '') {
  const needle = query.trim().toLocaleLowerCase('vi-VN')
  return data.entries.filter(entry => (!module || entry.policy.module === module)
    && (!differencesOnly || entry.roles.some(item => (!role || item.role === role) && !item.matches))
    && (!status || entry.roles.some(item => (!role || item.role === role) && comparisonStatus(item) === status))
    && (!needle || (entry.policy.label + ' ' + entry.policy.path + ' ' + entry.policy.id).toLocaleLowerCase('vi-VN').includes(needle)))
}
export function comparisonStatus(role: ReviewedEntry['roles'][number]): 'MATCH' | 'DIFFERENT' | 'UNKNOWN' {
  return role.documented === 'UNKNOWN' || role.actualGate === 'UNKNOWN' ? 'UNKNOWN' : role.matches ? 'MATCH' : 'DIFFERENT'
}
export function parseReportFile(text: string): ImportReport {
  let value: unknown
  try { value = JSON.parse(text) } catch { throw new Error('Tệp không phải JSON hợp lệ.') }
  if (!value || typeof value !== 'object') throw new Error('Tệp không chứa biên bản kiểm thử.')
  const report = value as Partial<ImportReport>
  if (![1, 2].includes(report.schemaVersion ?? 0) || !['REGRESSION', 'SIMULATION'].includes(report.kind ?? '')
    || !Array.isArray(report.checks) || !report.checks.length || report.checks.length > 5000
    || typeof report.fingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(report.fingerprint)) {
    throw new Error('Biên bản sai định dạng kiểm thử phân quyền hoặc không có kết quả kiểm thử.')
  }
  return report as ImportReport
}
export const formatReviewTime = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value))

export function downloadReport(detail: ReportDetail, exported?: unknown) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(exported ?? detail, null, 2)], { type: 'application/json;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = 'BienBan_S4-09.2_' + detail.summary.id + '.json'
  try { document.body.appendChild(link); link.click() }
  finally { link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
````

## frontend/tests/permissionReview.browser.cjs

````javascript
// Optional browser QA, matching the existing *.browser.cjs convention. Fixtures never enter application code.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..'), port = Number(process.env.LIBRA_QA_PORT || 5183)
const base = 'http://127.0.0.1:' + port
const policy = JSON.parse(fs.readFileSync(path.join(root, '../backend/src/main/resources/permissions/S4-09.2-policy.json'), 'utf8'))
function inspection(excess = false) {
  return { policyVersion: policy.version, decision: policy.decision, fingerprint: 'a'.repeat(64), inspectedAt: '2026-10-15T02:41:00Z',
    roles: policy.roles, modules: policy.modules, reviewedFunctions: policy.entries.length, totalApis: 191, undocumentedApis: [], differentFunctions: excess ? 1 : 0, unresolvedFunctions: 0,
    entries: policy.entries.map(entry => ({ policy: entry, mapped: true, guard: "hasRole('LIBRARIAN')", different: excess && entry.id === 'FeePaymentController.collect',
      roles: policy.roles.map(role => ({ role: role.code, documented: entry.rules[role.code], actualGate: excess && entry.id === 'FeePaymentController.collect' && role.code === 'READER' ? 'ALLOW' : entry.rules[role.code] === 'DENY' ? 'DENY' : 'ALLOW',
        matches: !(excess && entry.id === 'FeePaymentController.collect' && role.code === 'READER') })) })) }
}
const evidence = { schemaVersion: 1, runKey: '4b40f1ca-5cb8-47d7-b293-eeb16f63466a', policyVersion: policy.version, fingerprint: 'a'.repeat(64), kind: 'SIMULATION',
  environment: 'MOCKMVC', sourceReference: 'UI fixtures only', executedBy: 'Người chạy thử', startedAt: '2026-10-15T02:41:00Z', completedAt: '2026-10-15T02:42:00Z',
  checks: [{ endpointId: 'FeePaymentController.collect', role: 'READER', scenario: 'STANDARD', expectedStatus: 403, beforeStatus: 200, actualStatus: 403,
    resourceUnchanged: true, detectedAt: '2026-10-15T02:41:10Z', testedAt: '2026-10-15T02:41:50Z', evidence: 'Sổ thử không đổi sau HTTP 403.', correction: 'Khôi phục guard đúng trong mô phỏng.' }] }
const detail = { summary: { id: 1, kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'UI fixtures only', executedBy: evidence.executedBy, importedBy: 'Quản trị thử',
  importedAt: '2026-10-15T02:43:00Z', completedAt: evidence.completedAt, caseCount: 1, detectedCount: 1, fixedCount: 1, failedCount: 0 }, evidence, currentFingerprint: true,
  results: [{ test: evidence.checks[0], module: 'FEES', functionName: 'Thu toàn bộ phí', documented: 'DENY', outcome: 'FIXED', passed: true }] }
async function authenticate(page, role) {
  await page.evaluate(role => {
    localStorage.setItem('libra_access_token', 'synthetic-ui-access'); localStorage.setItem('libra_refresh_token', 'synthetic-ui-refresh')
    localStorage.setItem('libra_access_expires_at', String(Date.now() + 3600000)); localStorage.setItem('libra_refresh_expires_at', String(Date.now() + 86400000))
    localStorage.setItem('libra_current_user', JSON.stringify({ id: 1, fullName: 'Người thử giao diện', email: 'ui@example.invalid', role }))
  }, role)
}
;(async () => {
  const { createServer } = await import('vite')
  const server = await createServer({ root, server: { host: '127.0.0.1', port, strictPort: true } }); await server.listen()
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LIBRA_BROWSER_EXECUTABLE || undefined,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] })
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = [], results = []; let role = 'ADMIN', excess = false, rejectImport = false, imports = 0, reviewReads = 0, saved = false, job = null, runRequests = 0, jobPolls = 0, eligible = false, confirmed = false, exports = 0
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname.replace('/api/v1', '')
    let data = [], status = 200
    if (pathname === '/auth/me') data = { id: 1, role, fullName: 'Người thử giao diện', email: 'ui@example.invalid' }
    else if (pathname === '/permission-review/runs' && route.request().method() === 'POST') { runRequests++; job = { runKey: '00000000-0000-0000-0000-000000000001', state: 'RUNNING', message: 'Đang kiểm thử trên database riêng.', requestedBy: 'Quản trị thử', startedAt: '2030-01-01T00:00:00Z', finishedAt: null, reportId: null }; status = 202; data = job }
    else if (pathname === '/permission-review/runs/latest') data = job
    else if (pathname.startsWith('/permission-review/runs/')) { jobPolls++; job = { ...job, state: 'SUCCEEDED', message: 'Đã chạy xong kiểm thử.', reportId: 1 }; saved = true; data = job }
    else if (pathname === '/permission-review/reports/1/coverage' || pathname === '/permission-review/reports/1/complete') {
      if (pathname.endsWith('/complete')) confirmed = true
      data = { totalApis: 191, coveredApis: eligible ? 191 : 1, requiredRoleCases: 764, coveredRoleCases: eligible ? 764 : 1, passedCases: eligible ? 975 : 1, failedCases: 0, unresolvedFindings: 0, matrixDifferences: 0, unknownFunctions: 0, currentSource: true, serverExecuted: eligible, eligible, missing: [], reasons: eligible ? [] : ['Còn thiếu kiểm thử theo vai trò.'], completion: confirmed ? { reportId: 1, confirmedBy: 'Quản trị thử', confirmedAt: '2030-01-01T00:01:00Z', fingerprint: 'a'.repeat(64) } : null }
    }
    else if (pathname === '/permission-review/reports/1/export') { exports++; data = { report: detail, coverage: { eligible }, sprintReports: [detail] } }
    else if (pathname === '/permission-review') { reviewReads++; data = inspection(excess) }
    else if (pathname === '/permission-review/reports' && route.request().method() === 'POST') {
      imports++; status = rejectImport ? 409 : 201; saved = !rejectImport
      data = rejectImport ? { code: 'PERMISSION_BUILD_CHANGED', message: 'Source đã thay đổi. Chạy lại kiểm thử.' } : detail
    } else if (pathname === '/permission-review/reports') data = { items: saved ? [detail.summary] : [], page: 0, size: 10, totalItems: saved ? 1 : 0 }
    else if (pathname === '/permission-review/reports/1') data = detail
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  })
  async function check(name, action) {
    try { await action(); results.push({ name, result: 'PASS' }); console.log('PASS', name) }
    catch (error) { console.log('UI STATE', (await page.locator('body').innerText()).slice(-5000)); throw error }
  }
  async function overflow() {
    const diagnostics = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
      offenders: [...document.querySelectorAll('main *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1)
        .slice(0, 8).map(element => ({ tag: element.tagName, className: element.className, width: element.getBoundingClientRect().width, text: element.textContent?.slice(0, 80) })) }))
    assert.ok(diagnostics.scroll <= diagnostics.width + 1, JSON.stringify(diagnostics))
  }
  const file = value => ({ name: 'evidence.json', mimeType: 'application/json', buffer: Buffer.from(value) })
  try {
    await page.goto(base + '/login'); await authenticate(page, role); await page.goto(base + '/permission-review')
    await page.getByRole('heading', { name: 'Rà soát phân quyền', exact: true }).waitFor()
    await check('Admin sees all four roles and filters the fee API', async () => {
      await page.getByLabel('Phân hệ').selectOption('FEES')
      await page.getByLabel('Tìm chức năng hoặc API').fill('collect-full')
      await page.getByRole('heading', { name: 'Thu toàn bộ phí', exact: true }).waitFor()
      for (const label of policy.roles.map(value => value.name)) assert.ok(await page.getByText(label, { exact: true }).count() >= 1)
      assert.equal(await page.getByText('1 / 190 chức năng khớp bộ lọc', { exact: true }).count(), 1)
    })
    for (const width of [1366, 768, 390]) await check('Responsive matrix ' + width, async () => {
      await page.setViewportSize({ width, height: 1000 })
      await page.evaluate(() => Promise.all(document.getAnimations().filter(animation => animation.effect?.getTiming().iterations !== Infinity)
        .map(animation => animation.finished.catch(() => {}))))
      await overflow()
      if (width >= 1024) {
        const sidebar = await page.locator('.desktop-sidebar').boundingBox()
        const heading = await page.getByRole('heading', { name: 'Rà soát phân quyền', exact: true }).boundingBox()
        assert.ok(heading.x >= sidebar.x + sidebar.width, 'heading must not be hidden behind the sidebar')
      }
      if (process.env.LIBRA_QA_OUTPUT) {
        fs.mkdirSync(process.env.LIBRA_QA_OUTPUT, { recursive: true })
        await page.screenshot({ path: path.join(process.env.LIBRA_QA_OUTPUT, 'permission-review-' + width + '.png'), fullPage: true, animations: 'disabled' })
      }
    })
    await page.setViewportSize({ width: 1366, height: 1000 })
    await check('Discrepancy filter exposes a test-only excess permission', async () => {
      excess = true; await page.getByRole('button', { name: 'Làm mới', exact: true }).click()
      await page.getByText('Cần kiểm tra', { exact: true }).waitFor()
      await page.getByLabel('Chỉ hiện sai lệch hoặc chưa xác định').check()
      assert.equal(await page.getByRole('heading', { name: 'Thu toàn bộ phí', exact: true }).count(), 1)
    })
    await page.clock.install({ time: new Date('2030-01-01T00:00:00Z') })
    await page.clock.pauseAt(new Date('2030-01-01T00:00:05Z'))
    await check('Invalid-file warning disappears at 3000ms; fixed validation stays', async () => {
      await page.locator('input[type=file]').setInputFiles(file('broken'))
      const notice = page.locator('[data-feedback-tone=warning]'); await notice.waitFor()
      await page.clock.runFor(2999); assert.equal(await notice.count(), 1)
      await page.clock.runFor(1); assert.equal(await notice.count(), 0)
      assert.equal(await page.getByText('Tệp không phải JSON hợp lệ.', { exact: true }).count(), 1)
    })
    await check('Import and persistent simulation report survive 3000ms success dismissal', async () => {
      await page.locator('input[type=file]').setInputFiles(file(JSON.stringify(evidence)))
      await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).click()
      const dialog = page.getByRole('dialog'); await dialog.waitFor()
      assert.equal(imports, 1); assert.ok((await dialog.textContent()).includes('MÔ PHỎNG'))
      await dialog.locator('[data-feedback-tone=success]').waitFor()
      await page.clock.runFor(2999); assert.equal(await dialog.locator('[data-feedback-tone=success]').count(), 1)
      await page.clock.runFor(1); assert.equal(await dialog.locator('[data-feedback-tone=success]').count(), 0)
      assert.equal(await dialog.count(), 1)
      await page.setViewportSize({ width: 390, height: 900 }); await overflow()
      assert.ok((await dialog.boundingBox()).width <= 390)
      await page.getByRole('button', { name: 'Đóng biên bản', exact: true }).click()
    })
    await check('Rejected report retains retry input and a 3000ms API-error notice', async () => {
      rejectImport = true
      await page.locator('input[type=file]').setInputFiles(file(JSON.stringify(evidence)))
      await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).click()
      await page.locator('[data-feedback-tone=error]').waitFor()
      await page.clock.runFor(3000); assert.equal(await page.locator('[data-feedback-tone=error]').count(), 0)
      assert.ok(await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).isEnabled())
    })
    await check('Role and comparison filters compose without hiding a discrepancy for the selected role', async () => {
      await page.setViewportSize({ width: 1366, height: 1000 })
      await page.getByLabel('Vai trò', { exact: true }).selectOption('READER')
      await page.getByLabel('Trạng thái đối chiếu', { exact: true }).selectOption('DIFFERENT')
      assert.equal(await page.getByRole('heading', { name: 'Thu toàn bộ phí', exact: true }).count(), 1)
      await page.getByLabel('Vai trò', { exact: true }).selectOption('ADMIN')
      await page.getByText('Không có sai lệch cổng quyền theo bộ lọc', { exact: true }).waitFor()
      await page.getByLabel('Vai trò', { exact: true }).selectOption('')
      await page.getByLabel('Trạng thái đối chiếu', { exact: true }).selectOption('')
    })
    await check('Partial imported evidence cannot confirm completion', async () => {
      await page.getByRole('button', { name: 'Xem biên bản', exact: true }).first().click()
      await page.getByText('Còn thiếu kiểm thử theo vai trò.', { exact: true }).waitFor()
      assert.ok(await page.getByRole('button', { name: 'Xác nhận hoàn tất rà soát', exact: true }).isDisabled())
      await page.getByRole('button', { name: 'Đóng biên bản', exact: true }).click()
    })
    await check('Run-all prevents double submit and polling opens a completed run', async () => {
      await page.getByRole('button', { name: 'Chạy kiểm thử toàn bộ', exact: true }).click()
      await page.getByText('Đang kiểm thử trên database riêng.', { exact: true }).waitFor()
      assert.ok(await page.getByRole('button', { name: 'Chạy kiểm thử toàn bộ', exact: true }).isDisabled())
      assert.equal(runRequests, 1)
      await page.clock.runFor(2000)
      await page.getByRole('button', { name: 'Xem kết quả kiểm thử', exact: true }).waitFor()
      assert.equal(jobPolls, 1)
    })
    await check('Eligible run requires explicit confirmation and exports the full sprint report', async () => {
      eligible = true
      await page.getByRole('button', { name: 'Xem kết quả kiểm thử', exact: true }).click()
      await page.getByRole('button', { name: 'Xác nhận hoàn tất rà soát', exact: true }).waitFor()
      await page.getByRole('button', { name: 'Xác nhận hoàn tất rà soát', exact: true }).click()
      await page.clock.runFor(4000)
      assert.ok(await page.getByRole('button', { name: 'Đồng ý xác nhận', exact: true }).isVisible())
      await page.setViewportSize({ width: 390, height: 844 }); await overflow()
      if (process.env.LIBRA_QA_OUTPUT) await page.screenshot({ path: path.join(process.env.LIBRA_QA_OUTPUT, 'permission-review-confirm-390.png'), animations: 'disabled' })
      await page.setViewportSize({ width: 1366, height: 1000 })
      await page.getByRole('button', { name: 'Đồng ý xác nhận', exact: true }).click()
      await page.getByText(/Đã xác nhận bởi Quản trị thử/).waitFor()
      await page.clock.runFor(3000)
      assert.ok(await page.getByRole('dialog').isVisible())
      const download = page.waitForEvent('download')
      await page.getByRole('button', { name: 'Xuất biên bản đầy đủ', exact: true }).click()
      assert.match((await download).suggestedFilename(), /BienBan_S4-09\.2/)
      assert.equal(exports, 1)
      await page.getByRole('button', { name: 'Đóng biên bản', exact: true }).click()
    })
    for (role of ['READER', 'LIBRARIAN', 'LIBRARY_MANAGER']) await check(role + ' cannot navigate directly or see the review menu', async () => {
      const before = reviewReads; await authenticate(page, role); await page.goto(base + '/permission-review')
      await page.getByRole('heading', { name: 'Không có quyền truy cập' }).waitFor()
      assert.equal(reviewReads, before); assert.equal(await page.getByRole('link', { name: 'Rà soát phân quyền', exact: true }).count(), 0)
      await overflow()
    })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ checks: results.length, results }, null, 2))
  } finally { await browser.close(); await server.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
````

## frontend/tests/permissionReview.test.mjs

````javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s4-09-permission-review/', import.meta.url)
function load(name, imports = {}, globals = {}) {
  const context = { exports: {}, require: key => imports[key] ?? require(key), ...globals }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(name, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  return context.exports
}
const policy = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/permissions/S4-09.2-policy.json', import.meta.url)))
const inspection = { policyVersion: policy.version, roles: policy.roles, modules: policy.modules, fingerprint: 'a'.repeat(64),
  decision: policy.decision, inspectedAt: '2026-10-15T02:41:00Z', reviewedFunctions: policy.entries.length,
  differentFunctions: 0, unresolvedFunctions: 0, entries: policy.entries.map(entry => ({ policy: entry, guard: "hasRole('LIBRARIAN')", mapped: true,
    different: false, roles: policy.roles.map(role => ({ role: role.code, documented: entry.rules[role.code], actualGate: entry.rules[role.code] === 'DENY' ? 'DENY' : 'ALLOW', matches: true })) })) }
const report = { schemaVersion: 1, runKey: '4b40f1ca-5cb8-47d7-b293-eeb16f63466a', policyVersion: policy.version,
  fingerprint: 'a'.repeat(64), kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'Test only', executedBy: 'Người chạy thử',
  startedAt: '2026-10-15T02:41:00Z', completedAt: '2026-10-15T02:42:00Z', checks: [{ endpointId: 'FeePaymentController.collect', role: 'READER',
    scenario: 'STANDARD', expectedStatus: 403, beforeStatus: 200, actualStatus: 403, resourceUnchanged: true,
    detectedAt: '2026-10-15T02:41:10Z', testedAt: '2026-10-15T02:41:50Z', evidence: 'Mô phỏng', correction: 'Khôi phục guard thật' }] }
const detail = { summary: { id: 1, kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'Test only', executedBy: 'Người chạy thử',
  importedBy: 'Quản trị thử', importedAt: '2026-10-15T02:43:00Z', completedAt: report.completedAt, caseCount: 1, detectedCount: 1, fixedCount: 1, failedCount: 0 },
  evidence: report, currentFingerprint: true, results: [{ test: report.checks[0], module: 'FEES', functionName: 'Thu toàn bộ phí', documented: 'DENY', outcome: 'FIXED', passed: true }] }
const service = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {} } })

test('uses authenticated API client for review and reports, without accepting caller-supplied admin identity', async () => {
  const calls = [], api = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async (...args) => { calls.push(['GET', ...args]); return { data: {} } },
    post: async (...args) => { calls.push(['POST', ...args]); return { data: detail } },
  } } }).permissionReviewService
  await api.inspect(); await api.reports(2); await api.detail(1); await api.importReport(report)
  assert.deepEqual(calls.map(call => call[1]), ['/permission-review', '/permission-review/reports', '/permission-review/reports/1', '/permission-review/reports'])
  assert.equal(calls[1][2].params.page, 2); assert.equal(calls[3][2], report); assert.ok(!('actorId' in calls[3][2]))
})
test('module, keyword and discrepancy filters compose and cover all reviewed system modules', () => {
  for (const module of policy.modules) assert.ok(service.selectEntries(inspection, module.code, '', false).length)
  assert.equal(service.selectEntries(inspection, 'FEES', 'collect-full', false).length, 1)
  assert.equal(service.selectEntries(inspection, 'STOCK', 'collect-full', false).length, 0)
  assert.equal(service.selectEntries(inspection, '', '', true).length, 0)
  const changed = { ...inspection, entries: [{ ...inspection.entries[0], different: true, roles: inspection.entries[0].roles.map((role, i) => ({ ...role, matches: i !== 0 })) }, ...inspection.entries.slice(1)] }
  assert.equal(service.selectEntries(changed, '', '', true).length, 1)
})
test('malformed or empty evidence is rejected and simulated results retain their identity', () => {
  for (const value of ['broken', '{}', '[]', 'null', JSON.stringify({ ...report, checks: [] }), JSON.stringify({ ...report, fingerprint: 'bad' })]) {
    assert.throws(() => service.parseReportFile(value))
  }
  assert.equal(service.parseReportFile(JSON.stringify(report)).kind, 'SIMULATION')
})

const stub = component => ({ __esModule: true, default: component })
const Feedback = props => react.createElement('p', {}, props.message)
const Modal = props => props.children
function imports(role, api, hooks = react) {
  return { react: hooks,
    '../../components/ui/Button': stub(({ children, loading, ...props }) => react.createElement('button', { ...props, disabled: props.disabled || loading }, children)),
    '../../components/ui/Card': stub(({ children }) => react.createElement('section', {}, children)),
    '../../components/ui/EmptyState': stub(({ title, description }) => react.createElement('p', {}, title + description)),
    '../../components/ui/FeedbackAlert': stub(Feedback), '../../components/ui/LoadingState': stub(() => react.createElement('p', {}, 'Đang tải')),
    '../../components/ui/Modal': stub(Modal), '../../components/ui/PageHeader': stub(({ title }) => react.createElement('h1', {}, title)),
    '../../components/ui/TablePagination': stub(() => null),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (error, fallback) => error.message || fallback },
    './permissionReviewService': { ...service, permissionReviewService: api },
  }
}
test('direct navigation by non-admin renders denial and starts no review API request', () => {
  for (const role of ['LIBRARIAN', 'LIBRARY_MANAGER', 'READER', undefined]) {
    let calls = 0
    const page = load('PermissionReviewPage.tsx', imports(role, { inspect: () => { calls++ } })).default
    assert.match(renderToStaticMarkup(react.createElement(page)), /Không có quyền truy cập/)
    assert.equal(calls, 0)
  }
})
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const value of node) { const result = find(value, predicate); if (result) return result }; return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function harness(api = {}) {
  const slots = [], pending = []; let cursor = 0, tree
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]
      if (!old || deps.some((value, n) => !Object.is(value, old.deps[n]))) { slots[i] = { deps, cleanup: old?.cleanup }; pending.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() }) }
    },
  }
  const module = load('PermissionReviewPage.tsx', imports('ADMIN', {
    inspect: async () => inspection, reports: async () => ({ items: [], page: 0, size: 10, totalItems: 0 }),
    detail: async () => detail, importReport: async () => detail,
    latestRun: async () => null, coverage: async () => ({ totalApis: 191, coveredApis: 1, requiredRoleCases: 764, coveredRoleCases: 1, passedCases: 1, failedCases: 0, unresolvedFindings: 0, missing: [], eligible: false, reasons: ['Còn thiếu kiểm thử'], completion: null }),
    exportReport: async () => ({ report: detail, coverage: {}, sprintReports: [] }), ...api,
  }, hooks), { AbortController, File: class {} })
  function render() { cursor = 0; tree = module.PermissionReviewWorkspace(); for (const effect of pending.splice(0)) effect(); return tree }
  render()
  return { render, node: predicate => find(tree, predicate),
    notice: () => find(tree, value => value.type === Feedback), modal: () => find(tree, value => value.type === Modal),
    choose: file => find(tree, value => value.type === 'input' && value.props.type === 'file').props.onChange({ target: { files: [file] }, currentTarget: { value: 'selected-file' } }),
    unmount: () => slots.forEach(slot => slot?.cleanup?.()),
  }
}
const settle = () => new Promise(resolve => setImmediate(resolve))
test('invalid file produces temporary warning while fixed file validation remains after dismissal', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'broken.json', size: 10, text: async () => 'broken' }); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'warning')
  page.notice().props.onDismiss(); page.render(); assert.equal(page.notice(), undefined)
  assert.ok(page.node(node => node.type === 'p' && node.props.role === 'alert'))
  assert.equal(page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.disabled, true)
  page.unmount()
})
test('successful import resists double submit and keeps the report open after temporary success dismissal', async () => {
  let resolve, calls = 0
  const pending = new Promise(done => { resolve = done })
  const page = harness({ importReport: async () => { calls++; return pending } }); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  const button = page.node(node => node.props?.children?.[1] === 'Lưu biên bản')
  button.props.onClick(); button.props.onClick(); assert.equal(calls, 1)
  resolve(detail); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'success'); assert.ok(page.modal())
  page.notice().props.onDismiss(); page.render(); assert.equal(page.notice(), undefined); assert.ok(page.modal())
  assert.match(renderToStaticMarkup(page.modal()), /MÔ PHỎNG/)
  page.unmount()
})
test('API rejection leaves the selected evidence available for retry and shows error feedback', async () => {
  const page = harness({ importReport: async () => { throw new Error('Source đã thay đổi. Chạy lại kiểm thử.') } }); await settle(); page.render()
  page.choose({ name: 'regression.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'error'); assert.match(page.notice().props.message, /Source đã thay đổi/)
  assert.equal(page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.disabled, false)
  page.unmount()
})
test('closing the report early never remounts its temporary notification outside the modal', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render()
  assert.ok(page.notice()); assert.ok(page.modal())
  page.modal().props.onClose(); page.render()
  assert.equal(page.modal(), undefined); assert.equal(page.notice(), undefined)
  page.unmount()
})


test('role and comparison-state filters distinguish unknown rights from a concrete mismatch', () => {
  const row = inspection.entries[0]
  const sample = { ...inspection, entries: [{ ...row, different: true, roles: row.roles.map(role => ({ ...role, matches: false, documented: role.role === 'READER' ? 'UNKNOWN' : role.documented })) }] }
  assert.equal(service.selectEntries(sample, '', '', false, 'READER', 'UNKNOWN').length, 1)
  assert.equal(service.selectEntries(sample, '', '', false, 'ADMIN', 'UNKNOWN').length, 0)
  assert.equal(service.selectEntries(sample, '', '', false, 'ADMIN', 'DIFFERENT').length, 1)
})
test('server-run, progress, coverage, confirmation and export use protected review URLs', async () => {
  const calls = [], api = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async (...args) => { calls.push(['GET', ...args]); return { data: null } },
    post: async (...args) => { calls.push(['POST', ...args]); return { data: {} } },
  } } }).permissionReviewService
  await api.startRun(); await api.latestRun(); await api.runStatus('run-key'); await api.coverage(7); await api.complete(7); await api.exportReport(7)
  assert.deepEqual(calls.map(call => call[1]), ['/permission-review/runs', '/permission-review/runs/latest', '/permission-review/runs/run-key', '/permission-review/reports/7/coverage', '/permission-review/reports/7/complete', '/permission-review/reports/7/export'])
  assert.equal(calls[0].length, 2)
})
test('a missing-coverage report keeps completion disabled', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render(); await settle(); page.render()
  const button = page.node(node => node.props?.children?.[1] === 'Xác nhận hoàn tất rà soát')
  assert.equal(button.props.disabled, true); page.unmount()
})
````
