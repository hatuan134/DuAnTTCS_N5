package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.OverdueContactController;
import com.duanttcsn5.library.dto.loan.OverdueContactResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.OverdueContactService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OverdueContactControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ SecurityConfig.class, OverdueContactController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class })
    static class Config {
        @Bean OverdueContactService contacts() { return mock(OverdueContactService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private OverdueContactService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(OverdueContactService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { context.close(); }

    private void login(String roleCode) {
        Jwt jwt = Jwt.withTokenValue("contact-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("contact-token")).thenReturn(jwt);
        User user = new User(); user.setId(12L); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @Test void librarianCanRecordAndReadWithActorAndServerTimestamp() throws Exception {
        login("LIBRARIAN");
        var saved = new OverdueContactResponse(1L, 5L, 12L, "Thủ thư A",
                "Đã liên hệ", OffsetDateTime.parse("2026-10-09T17:30:00+07:00"));
        when(service.record(eq(5L), any(), eq(12L))).thenReturn(saved);
        when(service.history(5L, 12L)).thenReturn(List.of(saved));
        mvc.perform(post("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token")
                .contentType("application/json").content("{\"note\":\"Đã liên hệ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value(12))
                .andExpect(jsonPath("$.note").value("Đã liên hệ"));
        mvc.perform(get("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].staffName").value("Thủ thư A"));
    }

    @Test void blankNoteRejectedAndReaderCannotSeeContacts() throws Exception {
        login("LIBRARIAN");
        mvc.perform(post("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token")
                .contentType("application/json").content("{\"note\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        login("READER");
        mvc.perform(get("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token"))
                .andExpect(status().isForbidden());
    }

    @Test void managerCanViewAndRecordAccordingToPermissionMatrix() throws Exception {
        login("LIBRARY_MANAGER");
        when(service.history(5L, 12L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/loans/5/overdue-contacts")
                .header("Authorization", "Bearer contact-token")
                .contentType("application/json").content("{\"note\":\"Đã gọi\"}"))
                .andExpect(status().isCreated());
    }
}
