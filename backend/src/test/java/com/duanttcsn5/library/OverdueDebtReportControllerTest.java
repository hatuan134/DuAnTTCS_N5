package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.OverdueDebtReportController;
import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.OverdueDebtReportService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class OverdueDebtReportControllerTest {
    private static final String URL = "/api/v1/reports/overdue-debts";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, OverdueDebtReportController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean OverdueDebtReportService report() { return mock(OverdueDebtReportService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private OverdueDebtReportService reports;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        reports = context.getBean(OverdueDebtReportService.class);
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

    @Test void onlyManagerMayPreviewExportAndReadHistory() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL+"/export")).andExpect(status().isUnauthorized());
        mvc.perform(get(URL+"/history")).andExpect(status().isUnauthorized());
        for(String role:List.of("READER","LIBRARIAN","ADMIN")) {
            token(role);
            mvc.perform(get(URL).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
            mvc.perform(post(URL+"/export").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
            mvc.perform(get(URL+"/history").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports);
    }
    @Test void managerReceivesNonCacheableReportFileAndHistory() throws Exception {
        token("LIBRARY_MANAGER");
        var time=java.time.OffsetDateTime.parse("2026-10-15T00:01:00+07:00");
        when(reports.preview(12L)).thenReturn(new Snapshot(time,"filter",List.of(),0));
        when(reports.export(12L)).thenReturn(new Download("DanhSachQuaHan_NoPhi_15-10-2026.xlsx",new byte[]{80,75}));
        when(reports.history(12L,0,20)).thenReturn(new History(List.of(),0,20,0));
        mvc.perform(get(URL).header("Authorization","Bearer test-token")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReaders").value(0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
        mvc.perform(post(URL+"/export").header("Authorization","Bearer test-token")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",org.hamcrest.Matchers.containsString("DanhSachQuaHan_NoPhi_15-10-2026.xlsx")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get(URL+"/history").header("Authorization","Bearer test-token")).andExpect(status().isOk());
    }
}
