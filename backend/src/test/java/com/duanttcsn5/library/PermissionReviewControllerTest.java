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
