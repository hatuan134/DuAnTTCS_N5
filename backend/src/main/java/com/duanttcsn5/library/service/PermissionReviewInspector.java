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
        if (catalog.policy().version().startsWith("S4-09.2") || (catalog.policy().version().startsWith("S4-00.") && !catalog.policy().version().contains("priority"))) {
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
