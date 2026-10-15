# Toàn bộ file code hoàn chỉnh — S4-09.1

Mỗi mục dưới đây là toàn bộ file, không lược bỏ import hoặc thay bằng snippet. Khi dùng ZIP, ưu tiên copy file gốc tương ứng để giữ đúng cấu trúc. Tài liệu hướng dẫn nằm ở các file Markdown riêng.

## FILE 1

TRẠNG THÁI: THAY THẾ TOÀN BỘ FILE HIỆN TẠI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/config/SecurityConfig.java`

```java
package com.duanttcsn5.library.config;

import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.duanttcsn5.library.security.LongPasswordCompatibleEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LongPasswordCompatibleEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/initial-password/**",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password/**",
                                "/api/v1/readers/register",
                                "/api/v1/readers/check-duplicate",
                                "/api/v1/public/overview",
                                "/api/v1/books/public",
                                "/api/v1/books/public/**",
                                "/error")
                        .permitAll()
                        .requestMatchers("/api/v1/permission-review", "/api/v1/permission-review/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/readers/*/loan-history",
                                "/api/v1/readers/*/loan-history/export")
                        .hasAnyRole("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000",
                "http://127.0.0.1:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-CSV-Row-Count"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

## FILE 2

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/controller/PermissionReviewController.java`

```java
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
}
```

## FILE 3

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/dto/permission/PermissionReviewDtos.java`

```java
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
```

## FILE 4

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/repository/PermissionReviewRepository.java`

```java
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
```

## FILE 5

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCatalog.java`

```java
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

    public PermissionReviewCatalog(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("permissions/S4-09.1-policy.json").getInputStream()) {
            source = stream.readAllBytes();
        }
        policy = mapper.readValue(source, Policy.class);
        Map<String, PolicyEntry> indexed = new LinkedHashMap<>();
        Set<String> roleCodes = Set.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER");
        Set<String> modes = Set.of("ALLOW", "DENY", "OWN", "OVERRIDE");
        Set<String> moduleCodes = new HashSet<>();
        policy.modules().forEach(module -> moduleCodes.add(module.code()));
        for (PolicyEntry entry : policy.entries()) {
            if (indexed.put(entry.id(), entry) != null || !entry.rules().keySet().equals(roleCodes)
                    || !modes.containsAll(entry.rules().values()) || !moduleCodes.contains(entry.module())
                    || entry.httpMethods().isEmpty() || !entry.path().startsWith("/api/v1/")) {
                throw new IllegalStateException("Bảng quyền S4-09.1 không hợp lệ: " + entry.id());
            }
        }
        entries = Collections.unmodifiableMap(indexed);
    }

    public Policy policy() { return policy; }
    public byte[] source() { return source.clone(); }
    public Optional<PolicyEntry> find(String id) { return Optional.ofNullable(entries.get(id)); }

    public int requiredStatus(PolicyEntry entry, String role, String scenario, int successStatus) {
        if ("ANONYMOUS".equals(role)) return 401;
        String mode = entry.rules().getOrDefault(role, "DENY");
        if ("DENY".equals(mode)) return 403;
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
```

## FILE 6

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewInspector.java`

```java
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
        return new Inspection(catalog.policy().version(), catalog.policy().decision(), fingerprint(reviewed),
                OffsetDateTime.now(), catalog.policy().roles(), catalog.policy().modules(), reviewed.size(),
                (int) reviewed.stream().filter(ReviewedEntry::different).count(),
                (int) reviewed.stream().filter(entry -> entry.roles().stream()
                        .anyMatch(role -> "UNKNOWN".equals(role.actualGate()))).count(), reviewed);
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
            for (String controller : catalog.policy().entries().stream().map(PolicyEntry::controller).distinct().sorted().toList()) {
                String path = "/com/duanttcsn5/library/controller/" + controller + ".class";
                try (var stream = getClass().getResourceAsStream(path)) {
                    if (stream == null) throw new IllegalStateException("Thiếu bytecode controller: " + controller);
                    digest.update(stream.readAllBytes());
                }
            }
            // Bind evidence to compiled security AND business guards, not just the role document.
            for (Class<?> type : List.of(
                    com.duanttcsn5.library.config.SecurityConfig.class,
                    com.duanttcsn5.library.security.JwtAuthenticationFilter.class,
                    com.duanttcsn5.library.security.JwtService.class,
                    com.duanttcsn5.library.security.RestAuthenticationEntryPoint.class,
                    com.duanttcsn5.library.security.RestAccessDeniedHandler.class,
                    LoanService.class, ReaderRegistrationService.class, ReaderSelfService.class,
                    LibraryCardService.class, CardLockSuggestionService.class, BookReservationService.class,
                    ReservationAutoCancellationService.class, FeePaymentService.class, FeeReductionService.class,
                    BookCopyService.class, BookIncidentService.class, OverdueContactService.class,
                    com.duanttcsn5.library.repository.LoanRepository.class,
                    com.duanttcsn5.library.repository.BookReservationRepository.class,
                    com.duanttcsn5.library.repository.ReaderProfileRepository.class,
                    com.duanttcsn5.library.repository.UserRepository.class,
                    com.duanttcsn5.library.repository.LibraryCardRepository.class,
                    com.duanttcsn5.library.repository.BookCopyRepository.class,
                    com.duanttcsn5.library.repository.BookCopyLifecycleRepository.class,
                    com.duanttcsn5.library.repository.FeePaymentRepository.class,
                    com.duanttcsn5.library.repository.OverdueFeeRepository.class,
                    com.duanttcsn5.library.repository.BookIncidentRepository.class,
                    com.duanttcsn5.library.repository.CardLockSuggestionRepository.class,
                    com.duanttcsn5.library.repository.OverdueContactRepository.class)) {
                String path = "/" + type.getName().replace('.', '/') + ".class";
                try (var stream = type.getResourceAsStream(path)) {
                    if (stream == null) throw new IllegalStateException("Thiếu bytecode kiểm chứng: " + type.getSimpleName());
                    digest.update(type.getName().getBytes(StandardCharsets.UTF_8));
                    digest.update(stream.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được dấu vân tay phân quyền.", exception);
        }
    }
}
```

## FILE 7

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewService.java`

```java
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
            if (!keys.add(test.endpointId() + ":" + test.role() + ":" + test.scenario())) throw invalid("Biên bản chứa trường hợp kiểm thử trùng lặp.");
            int required;
            try { required = catalog.requiredStatus(entry, test.role(), test.scenario(), test.expectedStatus()); }
            catch (IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
            if (test.expectedStatus() != required) throw invalid("Kết quả mong đợi không khớp bảng quyền: " + test.endpointId() + " / " + test.role());
            if (test.detectedAt().isBefore(report.startedAt()) || test.testedAt().isAfter(report.completedAt())
                    || test.detectedAt().isAfter(test.testedAt())) throw invalid("Thời điểm phát hiện/kiểm thử lại không nằm trong lần chạy.");
            boolean denied = required == 401 || required == 403 || required == 404;
            boolean passed = test.actualStatus() == required
                    && (!entry.mutating() || !denied || Boolean.TRUE.equals(test.resourceUnchanged()));
            boolean foundBefore = test.beforeStatus() != null && test.beforeStatus() != required;
            if (foundBefore && passed && (test.correction() == null || test.correction().isBlank())) {
                throw invalid("Sai lệch đã khắc phục phải có nội dung sửa và bằng chứng kiểm thử lại.");
            }
            String outcome = !passed ? "OPEN" : foundBefore ? "FIXED" : "PASS";
            results.add(new CaseResult(test, entry.module(), entry.label(),
                    "ANONYMOUS".equals(test.role()) ? "LOGIN_REQUIRED" : entry.rules().get(test.role()), outcome, passed));
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
```

## FILE 8

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/resources/db/migration/V58__permission_review_records.sql`

```sql
-- S4-09.1: append-only imported evidence. No demo accounts or business data are seeded here.
CREATE TABLE permission_review_runs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    run_key UUID NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('REGRESSION', 'SIMULATION')),
    environment VARCHAR(30) NOT NULL CHECK (environment IN ('MOCKMVC', 'ISOLATED_POSTGRES')),
    policy_version VARCHAR(50) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    source_reference VARCHAR(200) NOT NULL,
    executed_by VARCHAR(200) NOT NULL,
    imported_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    imported_by_name VARCHAR(255) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NOT NULL,
    case_count INTEGER NOT NULL CHECK (case_count > 0),
    detected_count INTEGER NOT NULL CHECK (detected_count >= 0),
    fixed_count INTEGER NOT NULL CHECK (fixed_count >= 0 AND fixed_count <= detected_count),
    failed_count INTEGER NOT NULL CHECK (failed_count >= 0),
    evidence JSONB NOT NULL,
    results JSONB NOT NULL
);
CREATE INDEX ix_permission_review_runs_time ON permission_review_runs(imported_at DESC, id DESC);

CREATE FUNCTION reject_permission_review_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Account deletion may clear the FK; preserve the imported actor name and all evidence.
    IF TG_OP = 'UPDATE' AND OLD.imported_by_user_id IS NOT NULL AND NEW.imported_by_user_id IS NULL
       AND (to_jsonb(NEW) - 'imported_by_user_id') = (to_jsonb(OLD) - 'imported_by_user_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Permission review evidence is append-only';
END;
$$;
CREATE TRIGGER protect_permission_review_runs
BEFORE UPDATE OR DELETE ON permission_review_runs
FOR EACH ROW EXECUTE FUNCTION reject_permission_review_change();
```

## FILE 9

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/main/resources/permissions/S4-09.1-policy.json`

```json
{
  "version": "S4-09.1-v1",
  "decision": "Bảng quyền được người giao backlog chốt ngày 15/10/2026; Admin tách nhiệm vụ.",
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
    }
  ]
}
```

## FILE 10

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/CriticalPermissionApiTest.java`

```java
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
            RestAccessDeniedHandler.class, GlobalExceptionHandler.class, PermissionReviewCatalog.class,
            PermissionReviewInspector.class, ReaderController.class, LibraryCardController.class,
            LoanController.class, BookReservationController.class, FeePaymentController.class,
            FeeReductionController.class, BookCopyController.class, BookIncidentController.class,
            OverdueContactController.class, LibraryConfigurationController.class})
    static class Config {
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
```

## FILE 11

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/CriticalPermissionDatabaseTest.java`

```java
package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.service.PermissionReviewCatalog;
import com.duanttcsn5.library.service.PermissionReviewInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Explicit opt-in, disposable PostgreSQL only. Real login, signed JWT, real SQL; all fixtures roll back. */
@SpringBootTest(properties = "app.bootstrap-admin.password=")
@EnabledIfEnvironmentVariable(named = "S4_09_1_DB_TEST", matches = "true")
@Transactional
class CriticalPermissionDatabaseTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired PermissionReviewCatalog catalog;
    @Autowired PermissionReviewInspector inspector;
    MockMvc mvc;
    Map<String, Long> ids = new LinkedHashMap<>();
    Map<String, String> tokens = new HashMap<>();
    List<EvidenceCase> evidence = new ArrayList<>();
    OffsetDateTime started;
    Long book, copy, loan, item, reservation, fee;

    @Test void fourRoleLoginDeniedMutationsOwnershipFeeSuccessAndProtectedReportPersistence() throws Exception {
        started = OffsetDateTime.now();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String password = "S4091-Test-Only!";
        String encoded = encoder.encode(password);
        for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER", "OTHER_READER")) {
            String email = "s4091-" + UUID.randomUUID() + "@example.invalid";
            Long id = jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id",
                    Long.class, "S4091 " + role, email, encoded, role.equals("OTHER_READER") ? "READER" : role);
            ids.put(role, id);
            var login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
            assertEquals(200, login.getResponse().getStatus(), "Bốn vai trò phải đăng nhập được bằng mật khẩu thử.");
            tokens.put(role, mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText());
        }
        fixture();
        try {
            String direct = mapper.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "cardNumber", "S4091-CARD", "barcodes", List.of("S4091-BOOK")));
            String returned = mapper.writeValueAsString(Map.of("barcode", "S4091-BOOK", "itemId", item));
            String repair = "{\"reason\":\"Không được phép thay đổi kho\"}";
            probe("ReaderController.getReaderById", "READER", "STANDARD", "GET", "/api/v1/readers/" + ids.get("OTHER_READER"), null, 403);
            for (String role : List.of("READER", "ANONYMOUS", "LIBRARY_MANAGER")) {
                probe("LoanController.createDirectLoan", role, "STANDARD", "POST", "/api/v1/loans/direct", direct, role.equals("ANONYMOUS") ? 401 : 403);
            }
            probe("LoanController.confirmReturn", "READER", "STANDARD", "POST", "/api/v1/loans/return-confirmation", returned, 403);
            probe("BookCopyController.repair", "READER", "STANDARD", "POST", "/api/v1/book-copies/" + copy + "/repair", repair, 403);
            for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "LIBRARIAN")) {
                probe("BookReservationController.reserve", role, "STANDARD", "POST", "/api/v1/books/" + book + "/reservations", null, 403);
            }
            probe("LoanController.loanDetail", "READER", "STANDARD", "GET", "/api/v1/loans/" + loan, null, 200);
            var foreign = mvc.perform(MockMvcRequestBuilders.get("/api/v1/loans/" + loan)
                    .param("readerId", ids.get("READER").toString()).header("Authorization", "Bearer " + tokens.get("OTHER_READER"))).andReturn();
            assertEquals(404, foreign.getResponse().getStatus());
            assertFalse(foreign.getResponse().getContentAsString().contains("borrower_user_id"));
            evidence.add(new EvidenceCase("LoanController.loanDetail", "READER", "OTHER_OWNER", 404, null, 404, true,
                    started, OffsetDateTime.now(), "Hai tài khoản Bạn đọc thật: đổi ID phiếu và giả readerId vẫn HTTP 404, không trả chi tiết phiếu của người khác.", null));
            var preview = probe("FeePaymentController.detail", "LIBRARIAN", "STANDARD", "GET",
                    "/api/v1/fee-payments/readers/" + ids.get("READER"), null, 200);
            var debt = mapper.readTree(preview);
            String payment = mapper.writeValueAsString(Map.of("requestKey", UUID.randomUUID(), "expectedTotalVnd", 10000,
                    "debtToken", debt.get("debtToken").asText()));
            for (String role : List.of("ADMIN", "LIBRARY_MANAGER", "READER", "ANONYMOUS")) {
                probe("FeePaymentController.collect", role, "STANDARD", "POST",
                        "/api/v1/fee-payments/readers/" + ids.get("READER") + "/collect-full", payment, role.equals("ANONYMOUS") ? 401 : 403);
            }
            probe("FeePaymentController.collect", "LIBRARIAN", "STANDARD", "POST",
                    "/api/v1/fee-payments/readers/" + ids.get("READER") + "/collect-full", payment, 200);
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM fee_payments WHERE reader_user_id=?", Long.class, ids.get("READER")));
            assertEquals(10000L, jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?", Long.class, fee));

            ImportReport report = report();
            String json = mapper.writeValueAsString(report);
            var saved = mvc.perform(MockMvcRequestBuilders.post("/api/v1/permission-review/reports")
                    .header("Authorization", "Bearer " + tokens.get("ADMIN")).contentType("application/json").content(json)).andReturn();
            assertEquals(201, saved.getResponse().getStatus(), saved.getResponse().getContentAsString());
            Long reportId = mapper.readTree(saved.getResponse().getContentAsString()).get("summary").get("id").asLong();
            assertEquals(ids.get("ADMIN"), jdbc.queryForObject("SELECT imported_by_user_id FROM permission_review_runs WHERE id=?", Long.class, reportId));
            for (String role : List.of("READER", "LIBRARIAN", "LIBRARY_MANAGER")) {
                assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/permission-review/reports/" + reportId)
                        .header("Authorization", "Bearer " + tokens.get(role))).andReturn().getResponse().getStatus());
            }
            assertEquals(409, mvc.perform(MockMvcRequestBuilders.post("/api/v1/permission-review/reports")
                    .header("Authorization", "Bearer " + tokens.get("ADMIN")).contentType("application/json").content(json))
                    .andReturn().getResponse().getStatus());
        } finally {
            Path output = Path.of("target/permission-review/postgres.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report()));
        }
    }
    ImportReport report() {
        return new ImportReport(1, UUID.randomUUID(), catalog.policy().version(), inspector.inspect().fingerprint(),
                "REGRESSION", "ISOLATED_POSTGRES", "CriticalPermissionDatabaseTest / disposable PostgreSQL / rollback",
                System.getProperty("permission.review.executedBy", System.getProperty("user.name")), started, OffsetDateTime.now(), List.copyOf(evidence));
    }
    String probe(String endpoint, String role, String scenario, String method, String path, String body, int expected) throws Exception {
        String before = snapshot(); OffsetDateTime at = OffsetDateTime.now();
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        if (!role.equals("ANONYMOUS")) request.header("Authorization", "Bearer " + tokens.get(role));
        if (body != null) request.contentType("application/json").content(body);
        var result = mvc.perform(request).andReturn(); int actual = result.getResponse().getStatus();
        boolean denied = expected == 401 || expected == 403 || expected == 404;
        Boolean unchanged = denied ? before.equals(snapshot()) : null;
        evidence.add(new EvidenceCase(endpoint, role, scenario, expected, null, actual, unchanged, at, OffsetDateTime.now(),
                "PostgreSQL thật và JWT ký thật. Đối chiếu snapshot phiếu mượn, chi tiết trả, bản sao, đặt giữ, khoản nợ và giao dịch thu phí; dữ liệu thử rollback sau test.", null));
        assertEquals(expected, actual, result.getResponse().getContentAsString());
        if (denied) assertEquals(Boolean.TRUE, unchanged, "Yêu cầu bị từ chối đã thay đổi dữ liệu!");
        return result.getResponse().getContentAsString();
    }
    String snapshot() {
        List<Object> data = new ArrayList<>();
        for (var target : Map.of("loans", loan, "loan_items", item, "book_copies", copy, "book_reservations", reservation, "reader_fees", fee).entrySet()) {
            data.add(jdbc.queryForList("SELECT to_jsonb(t)::text AS row FROM " + target.getKey() + " t WHERE id=?", target.getValue()));
        }
        data.add(jdbc.queryForList("SELECT to_jsonb(t)::text AS row FROM fee_payments t WHERE reader_user_id=? ORDER BY id", ids.get("READER")));
        return mapper.writeValueAsString(data);
    }
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        Long warehouse = jdbc.queryForObject("INSERT INTO warehouses(code,name) VALUES (?,?) RETURNING id", Long.class, "S4091-" + suffix, "Kho S4091 " + suffix);
        Long shelf = jdbc.queryForObject("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,?) RETURNING id", Long.class, warehouse, "S4091", "Kệ thử");
        book = jdbc.queryForObject("INSERT INTO books(title,author_id,category_id) VALUES (?,(SELECT min(id) FROM authors),(SELECT min(id) FROM categories)) RETURNING id", Long.class, "S4091 " + suffix);
        copy = jdbc.queryForObject("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,?,'AVAILABLE',CURRENT_DATE-1,50000,'GOOD') RETURNING id", Long.class, book, "S4091-" + suffix, shelf);
        loan = jdbc.queryForObject("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id", Long.class, "S4091-" + suffix, ids.get("READER"), ids.get("LIBRARIAN"));
        item = jdbc.queryForObject("INSERT INTO loan_items(loan_id,book_copy_id,due_date) VALUES (?,?,CURRENT_TIMESTAMP+interval '14 days') RETURNING id", Long.class, loan, copy);
        reservation = jdbc.queryForObject("INSERT INTO book_reservations(book_id,reader_id) VALUES (?,?) RETURNING id", Long.class, book, ids.get("READER"));
        fee = jdbc.queryForObject("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'S4091 phí thử',10000) RETURNING id", Long.class, ids.get("READER"));
    }
}
```

## FILE 12

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/PermissionReviewControllerTest.java`

```java
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
        for (String path : List.of("/api/v1/permission-review", "/api/v1/permission-review/reports", "/api/v1/permission-review/reports/1")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/permission-review/reports").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void threeOtherRolesReceive403IncludingMalformedImportBody() throws Exception {
        for (String role : List.of("READER", "LIBRARIAN", "LIBRARY_MANAGER")) {
            token(role);
            for (String path : List.of("/api/v1/permission-review", "/api/v1/permission-review/reports", "/api/v1/permission-review/reports/1")) {
                mvc.perform(get(path).header("Authorization", "Bearer review")).andExpect(status().isForbidden());
            }
            mvc.perform(post("/api/v1/permission-review/reports").header("Authorization", "Bearer review")
                    .contentType("application/json").content("broken")).andExpect(status().isForbidden());
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
        verify(service, never()).importReport(any(), any());
    }
}
```

## FILE 13

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/PermissionReviewInspectorTest.java`

```java
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
}
```

## FILE 14

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/PermissionReviewServiceTest.java`

```java
package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionReviewServiceTest {
    final String fingerprint = "a".repeat(64);
    final OffsetDateTime start = OffsetDateTime.now().minusMinutes(1), end = start.plusSeconds(20);
    PermissionReviewCatalog catalog;
    PermissionReviewInspector inspector;
    PermissionReviewRepository repository;
    UserRepository users;
    PermissionReviewService service;
    @BeforeEach void setup() throws Exception {
        catalog = new PermissionReviewCatalog(new ObjectMapper());
        inspector = mock(PermissionReviewInspector.class); repository = mock(PermissionReviewRepository.class); users = mock(UserRepository.class);
        when(inspector.inspect()).thenReturn(new Inspection(catalog.policy().version(), "approved", fingerprint,
                end, catalog.policy().roles(), catalog.policy().modules(), 84, 0, 0, List.of()));
        var role = new Role(); role.setCode("ADMIN"); var actor = new User(); actor.setId(1L);
        actor.setRole(role); actor.setStatus("ACTIVE"); actor.setFullName("Quản trị kiểm thử");
        when(users.findById(1L)).thenReturn(Optional.of(actor));
        when(repository.insert(any(), anyList(), eq(1L), anyString())).thenReturn(9L);
        when(repository.find(9L, fingerprint)).thenReturn(Optional.of(mock(ReportDetail.class)));
        service = new PermissionReviewService(catalog, inspector, repository, users);
    }
    EvidenceCase check(int before, int after, Boolean unchanged, String correction) {
        return new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 403,
                before == 0 ? null : before, after, unchanged, start, end, "Bằng chứng HTTP và đối chiếu dữ liệu thử.", correction);
    }
    ImportReport report(String kind, String hash, List<EvidenceCase> checks) {
        return new ImportReport(1, UUID.randomUUID(), catalog.policy().version(), hash, kind, "MOCKMVC",
                "test source", "Người chạy thử", start, end, checks);
    }
    void rejects(Runnable call, HttpStatus status, String code) {
        ApiException error = assertThrows(ApiException.class, call::run);
        assertEquals(status, error.getStatus()); assertEquals(code, error.getCode());
    }
    @Test void fixedRequiresFailedBeforeSuccessfulRetestAndUnchangedResource() {
        var input = report("SIMULATION", fingerprint, List.of(check(200, 403, true, "Khôi phục cổng quyền đúng trong môi trường mô phỏng.")));
        service.importReport(input, 1L);
        verify(repository).insert(eq(input), argThat(results -> results.get(0).passed()
                && results.get(0).outcome().equals("FIXED")), eq(1L), eq("Quản trị kiểm thử"));
    }
    @Test void forbiddenStatusAloneCannotMarkMutatingEndpointFixed() {
        for (Boolean unchanged : Arrays.asList(false, null)) {
            var input = report("REGRESSION", fingerprint, List.of(check(200, 403, unchanged, "Thêm kiểm tra quyền.")));
            service.importReport(input, 1L);
            verify(repository).insert(eq(input), argThat(results -> !results.get(0).passed()
                    && results.get(0).outcome().equals("OPEN")), eq(1L), anyString());
        }
    }
    @Test void wrongRoleExpectationDuplicateCasesMissingCorrectionAndChangedBuildAreRejected() {
        var forged = new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 200,
                null, 200, null, start, end, "forged", null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(forged)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        var valid = check(0, 403, true, null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(valid, valid)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(check(200, 403, true, ""))), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.importReport(report("REGRESSION", "b".repeat(64), List.of(valid)), 1L), HttpStatus.CONFLICT, "PERMISSION_BUILD_CHANGED");
        verify(repository, never()).insert(any(), anyList(), anyLong(), anyString());
    }
    @Test void inactiveNonAdminUnknownActorAndDuplicateRunCannotImport() {
        var valid = report("REGRESSION", fingerprint, List.of(check(0, 403, true, null)));
        rejects(() -> service.importReport(valid, null), HttpStatus.FORBIDDEN, "FORBIDDEN");
        rejects(() -> service.importReport(valid, 999L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        var actor = users.findById(1L).orElseThrow(); actor.getRole().setCode("LIBRARIAN");
        rejects(() -> service.importReport(valid, 1L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        actor.getRole().setCode("ADMIN"); actor.setStatus("LOCKED");
        rejects(() -> service.importReport(valid, 1L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        actor.setStatus("ACTIVE"); when(repository.insert(any(), anyList(), anyLong(), anyString())).thenThrow(new DuplicateKeyException("duplicate"));
        rejects(() -> service.importReport(valid, 1L), HttpStatus.CONFLICT, "PERMISSION_REPORT_DUPLICATE");
    }
    @Test void importedTimesPaginationAndMissingReportAreValidated() {
        var outside = new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 403, null, 403,
                true, start.minusSeconds(1), end, "outside", null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(outside)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.reports(-1, 20), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.reports(0, 51), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.detail(0L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        when(repository.find(99L, fingerprint)).thenReturn(Optional.empty());
        rejects(() -> service.detail(99L), HttpStatus.NOT_FOUND, "PERMISSION_REPORT_NOT_FOUND");
    }
}
```

## FILE 15

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/java/com/duanttcsn5/library/PermissionReviewSimulationTest.java`

```java
package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.dto.loan.FeeReceiptResponse;
import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Deliberately incorrect controller exists ONLY in src/test, never in a runnable application profile. */
class PermissionReviewSimulationTest {
    static final AtomicInteger testLedger = new AtomicInteger();
    @RestController
    static class DeliberatelyWrongFeeController {
        @PostMapping("/api/v1/fee-payments/readers/{id}/collect-full")
        @PreAuthorize("hasRole('READER')")
        public Map<String, Integer> collect(@PathVariable Long id) {
            return Map.of("simulatedLedgerCount", testLedger.incrementAndGet());
        }
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class, GlobalExceptionHandler.class, DeliberatelyWrongFeeController.class})
    static class BeforeConfig {
        @Bean JwtService jwt() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
    AnnotationConfigWebApplicationContext context(Class<?> config) {
        var context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(config); context.refresh();
        for (String code : List.of("READER", "LIBRARIAN")) {
            long id = code.equals("READER") ? 12 : 13;
            var role = new Role(); role.setCode(code); var user = new User(); user.setId(id); user.setRole(role);
            user.setStatus("ACTIVE"); user.setTokenVersion(0);
            when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(user));
            when(context.getBean(JwtService.class).decode("review-" + code)).thenReturn(Jwt.withTokenValue("review-" + code)
                    .header("alg", "HS256").subject(Long.toString(id)).claim("tokenVersion", 0).build());
        }
        return context;
    }
    int collect(MockMvc mvc, String role) throws Exception {
        return mvc.perform(post("/api/v1/fee-payments/readers/1/collect-full")
                .header("Authorization", "Bearer review-" + role).contentType("application/json")
                .content("{\"requestKey\":\"4b40f1ca-5cb8-47d7-b293-eeb16f63466a\",\"expectedTotalVnd\":1,\"debtToken\":\"" + "a".repeat(64) + "\"}"))
                .andReturn().getResponse().getStatus();
    }
    @Test void detectsExcessAndMissingPermissionsAndProvesTheCorrectedGateDoesNotWrite() throws Exception {
        OffsetDateTime started = OffsetDateTime.now(); testLedger.set(0);
        int readerBefore, librarianBefore, readerAfter, librarianAfter;
        OffsetDateTime found;
        try (var before = context(BeforeConfig.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(before).apply(springSecurity()).build();
            readerBefore = collect(mvc, "READER"); librarianBefore = collect(mvc, "LIBRARIAN");
            assertEquals(200, readerBefore); assertEquals(403, librarianBefore); assertEquals(1, testLedger.get());
            found = OffsetDateTime.now();
        }
        try (var after = context(CriticalPermissionApiTest.Config.class)) {
            FeePaymentService service = after.getBean(FeePaymentService.class);
            doAnswer(invocation -> { testLedger.incrementAndGet(); return CriticalPermissionApiTest.sample(FeeReceiptResponse.class, "receipt", false); })
                    .when(service).collect(anyLong(), any(), anyLong());
            var mvc = MockMvcBuilders.webAppContextSetup(after).apply(springSecurity()).build();
            int unchanged = testLedger.get(); readerAfter = collect(mvc, "READER");
            assertEquals(403, readerAfter); assertEquals(unchanged, testLedger.get());
            librarianAfter = collect(mvc, "LIBRARIAN"); assertEquals(200, librarianAfter); assertEquals(unchanged + 1, testLedger.get());
            OffsetDateTime tested = OffsetDateTime.now();
            List<EvidenceCase> cases = List.of(
                    new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 403, readerBefore,
                            readerAfter, true, found, tested,
                            "MÔ PHỎNG: trước HTTP 200 làm sổ thử tăng 0→1; sau HTTP 403 giữ nguyên 1; Thủ thư vẫn thu được. Sổ thử trong bộ nhớ, không phải PostgreSQL.",
                            "Thay controller sai chỉ trong test bằng FeePaymentController thật. Không mở quyền Bạn đọc trên ứng dụng."),
                    new EvidenceCase("FeePaymentController.collect", "LIBRARIAN", "STANDARD", 200, librarianBefore,
                            librarianAfter, null, found, tested,
                            "MÔ PHỎNG quyền thiếu: Thủ thư trước HTTP 403; sau HTTP 200 và sổ thử tăng 1→2.",
                            "Controller thật phục hồi đúng quyền LIBRARIAN trong môi trường mô phỏng."));
            var report = new ImportReport(1, UUID.randomUUID(), after.getBean(PermissionReviewCatalog.class).policy().version(),
                    after.getBean(PermissionReviewInspector.class).inspect().fingerprint(), "SIMULATION", "MOCKMVC",
                    "S4-09.1 isolated intentionally-wrong controller / corrected real controller", System.getProperty("permission.review.executedBy", System.getProperty("user.name")),
                    started, OffsetDateTime.now(), cases);
            Path output = Path.of("target/permission-review/simulation.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, after.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}
```

## FILE 16

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `backend/src/test/resources/s4-09.1/demo-accounts.sql`

```sql
-- S4-09.1 OPTIONAL MANUAL QA ONLY. Run after Flyway on a disposable database named s4091_*.
-- Four accounts below use the test-only password: ReviewTest123!
-- This file is not a Flyway migration and is never run automatically.
BEGIN;
DO $$ BEGIN
    IF left(current_database(), 6) <> 's4091_' THEN
        RAISE EXCEPTION 'Chỉ chạy dữ liệu thử S4-09.1 trên database riêng có tên bắt đầu s4091_';
    END IF;
END $$;
INSERT INTO users(role_id,full_name,email,password_hash,status)
SELECT r.id, d.full_name, d.email, '$2b$12$CTzh0FW..UlkForpmn4.k.5M4PNnbutOS2.MQNkOWlZTXAyQnIsLq', 'ACTIVE'
FROM (VALUES
    ('ADMIN','S4091 Quản trị','s4091-admin@example.invalid'),
    ('LIBRARY_MANAGER','S4091 Quản lý','s4091-manager@example.invalid'),
    ('LIBRARIAN','S4091 Thủ thư','s4091-librarian@example.invalid'),
    ('READER','S4091 Bạn đọc','s4091-reader@example.invalid')
) d(role_code,full_name,email) JOIN roles r ON r.code=d.role_code
ON CONFLICT ((LOWER(email))) DO NOTHING;
INSERT INTO reader_profiles(user_id,member_code,registration_status)
SELECT id,'S4091-READER','APPROVED' FROM users WHERE email='s4091-reader@example.invalid'
ON CONFLICT (user_id) DO NOTHING;
INSERT INTO library_cards(card_number,user_id,card_type_id,expires_at,status)
SELECT 'S4091-CARD',u.id,(SELECT MIN(id) FROM card_types),CURRENT_DATE+365,'ACTIVE'
FROM users u WHERE email='s4091-reader@example.invalid'
AND NOT EXISTS (SELECT 1 FROM library_cards c WHERE c.user_id=u.id);
INSERT INTO reader_fees(reader_user_id,description,amount_vnd)
SELECT u.id,'S4091 phí thử nghiệm',10000 FROM users u
WHERE email='s4091-reader@example.invalid'
AND NOT EXISTS (SELECT 1 FROM reader_fees f WHERE f.reader_user_id=u.id AND f.description='S4091 phí thử nghiệm');
COMMIT;
```

## FILE 17

TRẠNG THÁI: THAY THẾ TOÀN BỘ FILE HIỆN TẠI

ĐƯỜNG DẪN: `frontend/src/app/navigation.ts`

```typescript
import type { NavItem } from '../types/feature'

const groups = [
  { id: 'overview', label: 'Tổng quan', paths: ['/dashboard'] },
  { id: 'people', label: 'Người dùng', paths: ['/users', '/readers', '/library-cards', '/card-lock-suggestions', '/change-password'] },
  { id: 'books', label: 'Quản lý sách', paths: ['/cataloging', '/authors', '/categories'] },
  { id: 'warehouse', label: 'Kho', paths: ['/warehouse-shelves'] },
  { id: 'loans', label: 'Mượn trả', paths: ['/loans', '/loans/direct', '/loans/search', '/loans/rejections', '/loans/receive-return', '/loans/overdue', '/my-borrowed-books', '/overdue-fees/reduction-requests/pending', '/fee-payments', '/loans/reminders'] },
  { id: 'reservations', label: 'Đặt giữ', paths: ['/reservations/ready-for-pickup', '/my-reservations', '/manager/auto-cancellations'] },
  { id: 'reports', label: 'Báo cáo', paths: ['/reports/loans', '/reports/overdue-debts', '/reports/overdue-debts/history'] },
  { id: 'settings', label: 'Quản trị', paths: ['/borrow-policy', '/library-calendar', '/card-lock-suggestion-settings', '/audit-log', '/permission-review'] },
]

export function visibleNavigation(items: NavItem[], role?: string) {
  return items.filter(item => !item.roles || Boolean(role && item.roles.includes(role)))
}

export function groupedNavigation(items: NavItem[]) {
  const result = groups.map(group => ({ ...group, items: items.filter(item => group.paths.includes(item.to)) })).filter(group => group.items.length)
  const extra = items.filter(item => !groups.some(group => group.paths.includes(item.to)))
  if (extra.length) result.push({ id: 'other', label: 'Chức năng khác', paths: [], items: extra })
  return result
}

export function activeNavigation(items: NavItem[], pathname: string) {
  // Prefer the most specific match, so /loans does not highlight together with /loans/search.
  const match = items.filter(item => pathname === item.to || pathname.startsWith(`${item.to}/`)).sort((a, b) => b.to.length - a.to.length)[0]
  if (match) return match.to
  if (/^\/books\//.test(pathname) || /^\/book-copies\//.test(pathname)) return '/cataloging'
  return ''
}
```

## FILE 18

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `frontend/src/features/s4-09-permission-review/PermissionReviewPage.tsx`

```tsx
import { useEffect, useRef, useState } from 'react'
import { Download, FileCheck2, RefreshCw, ShieldCheck, Upload, X } from 'lucide-react'
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
  downloadReport, formatReviewTime, gateLabels, parseReportFile, permissionReviewService, ruleLabels, selectEntries,
  type ImportReport, type Inspection, type ReportDetail, type ReportPage, type ReviewedEntry,
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

  async function chooseFile(selected?: File) {
    const version = ++fileVersion.current
    setFile(null); setFilename(selected?.name ?? ''); setFileError('')
    if (!selected) return
    try {
      if (selected.size > 1024 * 1024) throw new Error('Biên bản tối đa 1 MB. Chọn tệp JSON kết quả của bộ kiểm thử S4-09.1.')
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
  const entries = data ? selectEntries(data, module, query, differences) : []
  const pageSize = 10, totalPages = Math.max(1, Math.ceil(entries.length / pageSize)), currentPage = Math.min(page, totalPages)
  function resetFilter() { setPage(1) }
  const notify = feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />
  function closeReport() { setReport(null); setFeedback(null) }
  const paginationClass = 'px-3 sm:px-5 [&>div]:flex-wrap [&>div>span]:min-w-0'
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Rà soát phân quyền" description="Đối chiếu các API nghiệp vụ trọng yếu với bảng quyền đã chốt của bốn vai trò."
      action={<Button type="button" variant="secondary" disabled={loading || importing} onClick={() => setRevision(value => value + 1)}><RefreshCw size={16} />Làm mới</Button>} />
    {!report && notify}
    {loading ? <LoadingState /> : !data ? <Card className="p-6"><EmptyState title="Chưa tải được bảng phân quyền" description="Bấm Làm mới để thử lại khi kết nối máy chủ hoạt động." /></Card> : <>
      <div className="grid gap-3 sm:grid-cols-3">
        <Metric label="API / thao tác đã đối chiếu" value={data.reviewedFunctions} />
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
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm"><label className="flex min-h-11 items-center gap-2 text-slate-700"><input type="checkbox" checked={differences} onChange={event => { setDifferences(event.target.checked); resetFilter() }} />Chỉ hiện sai lệch hoặc chưa xác định</label>
          <span role="status" className="text-slate-500">{entries.length} / {data.reviewedFunctions} thao tác khớp bộ lọc</span></div>
      </Card>
      {entries.length ? <div className="space-y-4">{entries.slice((currentPage - 1) * pageSize, currentPage * pageSize).map(entry => <ComparisonCard key={entry.policy.id} entry={entry} data={data} />)}
        <TablePagination className={paginationClass} page={currentPage} totalItems={entries.length} totalPages={totalPages} pageSize={pageSize} onPageChange={setPage} /></div>
        : <Card className="p-5"><EmptyState title={differences ? 'Không có sai lệch cổng quyền theo bộ lọc' : 'Không có chức năng phù hợp'} description="Thay đổi phân hệ hoặc từ khóa để xem các thao tác khác. Kết quả này không thay thế kiểm thử API và kiểm thử sở hữu dữ liệu." /></Card>}
    </>}
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
      {reportsLoading ? <LoadingState /> : !reports ? <p className="p-5 text-sm text-slate-600">Chưa tải được biên bản. Bấm Làm mới để thử lại.</p> : !reports.items.length ? <div className="p-5"><EmptyState title="Chưa có biên bản" description="Chạy bộ kiểm thử trên môi trường riêng, sau đó nhập tệp kết quả ở phía trên." /></div> : <>
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
          <p className="rounded-xl bg-slate-50 p-3 text-sm leading-6 text-slate-700">{report.summary.kind === 'SIMULATION' ? 'MÔ PHỎNG: sai lệch được tạo có chủ đích trong môi trường kiểm thử riêng, không phải kết luận lỗi thật của hệ thống.' : 'Kết quả bộ kiểm thử được Admin nhập từ môi trường riêng; không phải một lần chạy API trên máy chủ này.'}</p>
          {!report.currentFingerprint && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">Biên bản thuộc bản code khác. Cần chạy lại để xác nhận trạng thái hiện tại.</p>}
          <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Người chạy kiểm thử</dt><dd className="break-words font-medium">{report.summary.executedBy}</dd></div><div><dt className="text-slate-500">Nguồn kiểm thử</dt><dd className="break-words font-medium">{report.summary.sourceReference}</dd></div><div><dt className="text-slate-500">Người nhập biên bản</dt><dd className="break-words font-medium">{report.summary.importedBy}</dd></div><div><dt className="text-slate-500">Thời điểm nhập</dt><dd>{formatReviewTime(report.summary.importedAt)}</dd></div></dl>
          <div className="grid gap-3 sm:grid-cols-3"><Metric label="Sai lệch phát hiện" value={report.summary.detectedCount} /><Metric label="Đã khắc phục" value={report.summary.fixedCount} /><Metric label="Chưa đạt" value={report.summary.failedCount} warning={report.summary.failedCount > 0} /></div>
          <Button type="button" variant="secondary" onClick={() => { try { downloadReport(report); setFeedback({ tone: 'success', message: 'Đã tạo tệp biên bản JSON.' }) } catch { setFeedback({ tone: 'error', message: 'Không tạo được tệp biên bản. Vui lòng thử lại.' }) } }}><Download size={16} />Tải biên bản JSON</Button>
          {report.results.map(result => <article key={result.test.endpointId + result.test.role + result.test.scenario} className="space-y-2 rounded-xl border border-slate-200 p-4 text-sm">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold text-slate-900">{result.functionName}</h3><span className={'rounded-lg px-2 py-1 text-xs font-semibold ' + (result.passed ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700')}>{result.outcome === 'FIXED' ? 'Đã khắc phục' : result.passed ? 'Đạt' : 'Chưa đạt'}</span></div>
            <p className="break-words text-slate-600">{data?.modules.find(item => item.code === result.module)?.name ?? result.module} · {data?.roles.find(item => item.code === result.test.role)?.name ?? 'Chưa đăng nhập'} · {result.test.scenario}</p>
            <p>Theo tài liệu: {ruleLabels[result.documented]} · HTTP mong đợi: {result.test.expectedStatus}</p>
            <p>Trước: {result.test.beforeStatus ?? 'Không có sai lệch trước đó'} → Kiểm thử lại: <strong>{result.test.actualStatus}</strong></p>
            <p>Dữ liệu sau yêu cầu bị từ chối: {result.test.resourceUnchanged === true ? 'Không thay đổi' : result.test.resourceUnchanged === false ? 'Có thay đổi' : 'Không áp dụng / chưa kiểm tra'}</p>
            <p className="break-words leading-6 text-slate-600">Bằng chứng: {result.test.evidence}</p>
            {result.test.correction && <p className="break-words leading-6">Nội dung sửa: {result.test.correction}</p>}
            <p className="text-xs leading-5 text-slate-500">Phát hiện: {formatReviewTime(result.test.detectedAt)} · Kiểm thử lại: {formatReviewTime(result.test.testedAt)}</p>
          </article>)}
        </div>
      </section>
    </Modal>}
  </div>
}

function Metric({ label, value, warning = false }: { label: string; value: number; warning?: boolean }) {
  return <Card className="p-4"><p className="text-sm text-slate-500">{label}</p><p className={'mt-2 text-2xl font-semibold ' + (warning ? 'text-red-700' : 'text-slate-900')}>{value.toLocaleString('vi-VN')}</p></Card>
}
function ComparisonCard({ entry, data }: { entry: ReviewedEntry; data: Inspection }) {
  return <Card className="min-w-0 space-y-4 p-4 sm:p-5">
    <div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="text-xs font-medium text-slate-500">{data.modules.find(item => item.code === entry.policy.module)?.name}</p><h3 className="mt-1 break-words font-semibold text-slate-900">{entry.policy.label}</h3></div>
      <span className={'rounded-lg px-2.5 py-1 text-xs font-semibold ' + (entry.different ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-800')}>{entry.different ? 'Cần kiểm tra' : 'Khớp cổng quyền'}</span></div>
    <code className="block break-all rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">{entry.policy.httpMethods.join(' / ')} {entry.policy.path}</code>
    <div className="grid min-w-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">{entry.roles.map(role => <div key={role.role} className={'min-w-0 rounded-xl border p-3 ' + (role.matches ? 'border-slate-200' : 'border-red-200 bg-red-50')}>
      <p className="break-words text-sm font-semibold text-slate-900">{data.roles.find(item => item.code === role.role)?.name}</p>
      <dl className="mt-3 space-y-2 text-xs leading-5"><div><dt className="text-slate-500">Theo tài liệu</dt><dd className="font-medium text-slate-800">{ruleLabels[role.documented]}</dd></div><div><dt className="text-slate-500">Cổng API thực tế</dt><dd className={role.matches ? 'text-slate-700' : 'font-semibold text-red-700'}>{gateLabels[role.actualGate]}</dd></div></dl>
    </div>)}</div>
    {entry.policy.condition && <p className="text-sm leading-6 text-slate-600">Điều kiện: {entry.policy.condition}</p>}
    {Object.values(entry.policy.rules).includes('OWN') && <p className="text-xs leading-5 text-slate-500">Dữ liệu cá nhân được xác định từ tài khoản đăng nhập; không lấy danh tính từ tham số do trình duyệt tự gửi.</p>}
    <details className="text-xs text-slate-500"><summary className="cursor-pointer">Xem căn cứ trong source</summary><p className="mt-2 break-all">{entry.policy.controller}.{entry.policy.handler}</p><code className="mt-1 block break-all">{entry.guard}</code></details>
  </Card>
}
```

## FILE 19

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `frontend/src/features/s4-09-permission-review/feature.tsx`

```tsx
import { ShieldCheck } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const PermissionReviewPage = loadablePage(() => import('./PermissionReviewPage'))
const feature: FeatureModule = {
  id: 's4-09-permission-review', order: 280,
  appRoutes: [{ path: 'permission-review', element: <PermissionReviewPage /> }],
  navItems: [{ label: 'Rà soát phân quyền', to: '/permission-review', icon: ShieldCheck, order: 280, roles: ['ADMIN'] }],
}
export default feature
```

## FILE 20

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `frontend/src/features/s4-09-permission-review/permissionReviewService.ts`

```typescript
import { apiClient } from '../../core/api/apiClient'

export type Rule = 'ALLOW' | 'DENY' | 'OWN' | 'OVERRIDE'
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
  differentFunctions: number; unresolvedFunctions: number; entries: ReviewedEntry[]
}
export interface EvidenceCase {
  endpointId: string; role: string; scenario: 'STANDARD' | 'OVERRIDE' | 'OTHER_OWNER'
  expectedStatus: number; beforeStatus: number | null; actualStatus: number
  resourceUnchanged: boolean | null; detectedAt: string; testedAt: string; evidence: string; correction: string | null
}
export interface ImportReport {
  schemaVersion: 1; runKey: string; policyVersion: string; fingerprint: string
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
const base = '/permission-review'
export const permissionReviewService = {
  async inspect(signal?: AbortSignal) { return (await apiClient.get<Inspection>(base, { signal })).data },
  async reports(page: number, signal?: AbortSignal) {
    return (await apiClient.get<ReportPage>(base + '/reports', { params: { page, size: 10 }, signal })).data
  },
  async detail(id: number, signal?: AbortSignal) { return (await apiClient.get<ReportDetail>(base + '/reports/' + id, { signal })).data },
  async importReport(report: ImportReport) { return (await apiClient.post<ReportDetail>(base + '/reports', report)).data },
}
export const ruleLabels: Record<Rule | 'LOGIN_REQUIRED', string> = {
  ALLOW: 'Được phép', DENY: 'Không được phép', OWN: 'Chỉ dữ liệu cá nhân',
  OVERRIDE: 'Ngoại lệ có lý do', LOGIN_REQUIRED: 'Phải đăng nhập',
}
export const gateLabels = { ALLOW: 'Cho qua cổng vai trò', DENY: 'Bị chặn tại cổng vai trò', UNKNOWN: 'Chưa xác định được' }
export function selectEntries(data: Inspection, module: string, query: string, differencesOnly: boolean) {
  const needle = query.trim().toLocaleLowerCase('vi-VN')
  return data.entries.filter(entry => (!module || entry.policy.module === module)
    && (!differencesOnly || entry.different)
    && (!needle || (entry.policy.label + ' ' + entry.policy.path + ' ' + entry.policy.id).toLocaleLowerCase('vi-VN').includes(needle)))
}
export function parseReportFile(text: string): ImportReport {
  let value: unknown
  try { value = JSON.parse(text) } catch { throw new Error('Tệp không phải JSON hợp lệ.') }
  if (!value || typeof value !== 'object') throw new Error('Tệp không chứa biên bản kiểm thử.')
  const report = value as Partial<ImportReport>
  if (report.schemaVersion !== 1 || !['REGRESSION', 'SIMULATION'].includes(report.kind ?? '')
    || !Array.isArray(report.checks) || !report.checks.length || report.checks.length > 2000
    || typeof report.fingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(report.fingerprint)) {
    throw new Error('Biên bản sai định dạng S4-09.1 hoặc không có kết quả kiểm thử.')
  }
  return report as ImportReport
}
export const formatReviewTime = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value))

export function downloadReport(detail: ReportDetail) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(detail, null, 2)], { type: 'application/json;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = 'BienBan_S4-09.1_' + detail.summary.id + '.json'
  try { document.body.appendChild(link); link.click() }
  finally { link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
```

## FILE 21

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `frontend/tests/permissionReview.browser.cjs`

```javascript
// Optional browser QA, matching the existing *.browser.cjs convention. Fixtures never enter application code.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..'), port = Number(process.env.LIBRA_QA_PORT || 5183)
const base = 'http://127.0.0.1:' + port
const policy = JSON.parse(fs.readFileSync(path.join(root, '../backend/src/main/resources/permissions/S4-09.1-policy.json'), 'utf8'))
function inspection(excess = false) {
  return { policyVersion: policy.version, decision: policy.decision, fingerprint: 'a'.repeat(64), inspectedAt: '2026-10-15T02:41:00Z',
    roles: policy.roles, modules: policy.modules, reviewedFunctions: policy.entries.length, differentFunctions: excess ? 1 : 0, unresolvedFunctions: 0,
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
  const errors = [], results = []; let role = 'ADMIN', excess = false, rejectImport = false, imports = 0, reviewReads = 0, saved = false
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname.replace('/api/v1', '')
    let data = [], status = 200
    if (pathname === '/auth/me') data = { id: 1, role, fullName: 'Người thử giao diện', email: 'ui@example.invalid' }
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
      assert.equal(await page.getByText('1 / 84 thao tác khớp bộ lọc', { exact: true }).count(), 1)
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
```

## FILE 22

TRẠNG THÁI: TẠO MỚI

ĐƯỜNG DẪN: `frontend/tests/permissionReview.test.mjs`

```javascript
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
const policy = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/permissions/S4-09.1-policy.json', import.meta.url)))
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
test('module, keyword and discrepancy filters compose and cover all five priority groups', () => {
  for (const module of policy.modules) assert.ok(service.selectEntries(inspection, module.code, '', false).length)
  assert.equal(service.selectEntries(inspection, 'FEES', 'collect-full', false).length, 1)
  assert.equal(service.selectEntries(inspection, 'STOCK', 'collect-full', false).length, 0)
  assert.equal(service.selectEntries(inspection, '', '', true).length, 0)
  const changed = { ...inspection, entries: [{ ...inspection.entries[0], different: true }, ...inspection.entries.slice(1)] }
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
    detail: async () => detail, importReport: async () => detail, ...api,
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
```
