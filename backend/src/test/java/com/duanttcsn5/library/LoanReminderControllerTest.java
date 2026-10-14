package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanReminderController;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.LoanReminderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanReminderControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, LoanReminderController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean
        LoanReminderService loanReminderService() {
            return mock(LoanReminderService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private LoanReminderService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(LoanReminderService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void authenticateWithRole(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("99").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);

        User user = new User();
        user.setId(99L);
        user.setFullName("Nhân Viên Thư Viện");
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode(roleCode);
        user.setRole(role);

        when(context.getBean(UserRepository.class).findById(99L)).thenReturn(Optional.of(user));
    }

    @Test
    void unauthenticatedAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/loan-reminders/preview"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/loan-reminders/trigger"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/loan-reminders/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerRoleIsForbiddenFromReminderManagement() throws Exception {
        authenticateWithRole("READER");

        mvc.perform(get("/api/v1/loan-reminders/preview")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/loan-reminders/trigger")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/loan-reminders/history")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void librarianCanPreviewTriggerAndReadHistory() throws Exception {
        authenticateWithRole("LIBRARIAN");

        LocalDate today = LocalDate.of(2026, 10, 14);
        when(service.getPreview(today)).thenReturn(new LoanReminderPreviewResponse(today, 2, 3, Collections.emptyList()));
        when(service.processDailyReminders(eq(today), eq("MANUAL_TEST_RUN"), eq(99L), eq("Nhân Viên Thư Viện")))
                .thenReturn(new LoanReminderExecutionResult(today, 2, 2, 0, 0, 3, "MANUAL_TEST_RUN"));
        when(service.getLogs(any(), any(), any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(service.countLogs(any(), any(), any())).thenReturn(0L);

        mvc.perform(get("/api/v1/loan-reminders/preview?date=2026-10-14")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipients").value(2))
                .andExpect(jsonPath("$.totalItems").value(3));

        mvc.perform(post("/api/v1/loan-reminders/trigger")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"2026-10-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(2))
                .andExpect(jsonPath("$.triggeredBy").value("MANUAL_TEST_RUN"));

        mvc.perform(get("/api/v1/loan-reminders/history")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(service).getPreview(today);
        verify(service).processDailyReminders(eq(today), eq("MANUAL_TEST_RUN"), eq(99L), eq("Nhân Viên Thư Viện"));
    }
}
