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
