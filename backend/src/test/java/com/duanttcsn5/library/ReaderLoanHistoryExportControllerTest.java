package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.ReaderController;
import com.duanttcsn5.library.dto.reader.ReaderLoanHistoryResponse;
import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.duanttcsn5.library.service.ReaderSelfService;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.ReaderRegistrationService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class ReaderLoanHistoryExportControllerTest {
    private static final String URL = "/api/v1/readers/20/loan-history/export";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ReaderController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean ReaderRegistrationService readers() { return mock(ReaderRegistrationService.class); }
        @Bean ReaderSelfService selfService() { return mock(ReaderSelfService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private ReaderRegistrationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(ReaderRegistrationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

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
    void exportIsDeniedBeforeBindingForOtherRolesAndMalformedIds() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"READER", "ADMIN", "AUDITOR", "UNKNOWN"}) {
            token(role);
            for (String id : new String[]{"20", "999", "0", "abc"}) {
                mvc.perform(get("/api/v1/readers/" + id + "/loan-history/export")
                        .param("fromDate", "invalid").header("Authorization", "Bearer test-token"))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void allowedRolesReceiveCsvAttachmentAndAppliedDates() throws Exception {
        var csv = new com.duanttcsn5.library.service.ReaderLoanHistoryCsv.Export(
                "history_BD20.csv", "\uFEFFMã phiếu\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0);
        when(service.exportReaderLoanHistory(20L, "2026-10-01", "2026-10-09")).thenReturn(csv);
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(get(URL).param("fromDate", "2026-10-01").param("toDate", "2026-10-09")
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(csv.content()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"history_BD20.csv\""))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-CSV-Row-Count", "0"));
        }
    }

    @Test
    void exportErrorsKeepJsonContract() throws Exception {
        token("LIBRARIAN");
        when(service.exportReaderLoanHistory(20L, "2026-10-09", "2026-10-01"))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_READER_HISTORY_DATE_RANGE", "Từ ngày không được lớn hơn Đến ngày."));
        mvc.perform(get(URL).param("fromDate", "2026-10-09").param("toDate", "2026-10-01")
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_READER_HISTORY_DATE_RANGE"));
    }

    @Test
    void corsExposesDownloadMetadata() {
        var cors = new SecurityConfig().corsConfigurationSource().getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest("GET", URL));
        org.assertj.core.api.Assertions.assertThat(cors.getExposedHeaders())
                .contains("Content-Disposition", "X-CSV-Row-Count");
    }
}
