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
