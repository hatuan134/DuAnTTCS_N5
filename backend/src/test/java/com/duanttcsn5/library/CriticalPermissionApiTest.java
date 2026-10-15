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
