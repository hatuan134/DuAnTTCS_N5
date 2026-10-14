package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanReportController;
import com.duanttcsn5.library.dto.report.LoanReportResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.LoanReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class LoanReportControllerTest {
    private static final String URL = "/api/v1/reports/loans";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, LoanReportController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LoanReportService report() { return mock(LoanReportService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private LoanReportService reports;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        reports = context.getBean(LoanReportService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    private void token(String roleCode) {
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(token);
        User user = new User();
        user.setId(12L);
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode(roleCode);
        user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @Test
    void onlyLibraryManagerMayReadReport() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"READER", "LIBRARIAN", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports);
    }

    @Test
    void managerGetsSummaryAndTopBooks() throws Exception {
        token("LIBRARY_MANAGER");
        when(reports.report(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14)))
                .thenReturn(new LoanReportResponse(LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 14), 500, 420, 80, 20, 25,
                        List.of(new LoanReportResponse.TopBorrowedBook(3, "Cơ sở dữ liệu", 65, 3)),
                        java.time.OffsetDateTime.parse("2026-10-14T10:00:00+07:00"),
                        List.of(new LoanReportResponse.TopQueuedBook(3, "Cơ sở dữ liệu", 12, 3))));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token")
                        .param("startDate", "2026-10-01").param("endDate", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBorrowed").value(500))
                .andExpect(jsonPath("$.totalReturned").value(420))
                .andExpect(jsonPath("$.openLoans").value(80))
                .andExpect(jsonPath("$.overdueLoans").value(20))
                .andExpect(jsonPath("$.overdueRate").value(25))
                .andExpect(jsonPath("$.topBooks[0].bookTitle").value("Cơ sở dữ liệu"))
                .andExpect(jsonPath("$.topQueuedBooks[0].waitCount").value(12))
                .andExpect(jsonPath("$.topQueuedBooks[0].stockCount").value(3))
                .andExpect(jsonPath("$.queueSnapshotAt").exists());
    }
}
