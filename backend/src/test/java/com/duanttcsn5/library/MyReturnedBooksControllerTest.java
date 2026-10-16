package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyReturnedBooksControllerTest {
    private static final String URL = "/api/v1/loans/me/returned-books";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LoanService loans() { return mock(LoanService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private LoanService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(LoanService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    private void token(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);
        User user = new User(); user.setId(12L); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }


    @Test void anonymousInvalidJwtAndStaffDeniedOnEveryPage() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(get(URL).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(get(URL).param("page", "1").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void currentReaderOnlyAndClientCannotIncreaseSize() throws Exception {
        token("READER");
        for (int page : new int[]{0, 1, 2}) {
            when(service.myReturnedBooks(12L, page)).thenReturn(new com.duanttcsn5.library.dto.loan.MyReturnedBooksPageResponse(
                    java.util.List.of(new com.duanttcsn5.library.dto.loan.MyReturnedBookResponse(21L, "Mắt biếc", "LIB-21", "PM-21",
                            java.time.OffsetDateTime.parse("2026-10-01T10:00:00Z"), java.time.OffsetDateTime.parse("2026-10-08T10:00:00Z"))), page, 20, 41));
            mvc.perform(get(URL).param("page", String.valueOf(page)).param("readerId", "99").param("size", "999")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.total").value(41))
                    .andExpect(jsonPath("$.items[0].loanNumber").value("PM-21"))
                    .andExpect(jsonPath("$.items[0].returnedAt").exists());
            verify(service).myReturnedBooks(12L, page);
        }
        verifyNoMoreInteractions(service);
    }
    @Test void invalidPageAndEmptyHistoryUseExistingErrorAndResponseConventions() throws Exception {
        token("READER");
        when(service.myReturnedBooks(12L, -1)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_HISTORY_PAGE", "Số trang lịch sử không hợp lệ."));
        mvc.perform(get(URL).param("page", "-1").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_HISTORY_PAGE"));
        mvc.perform(get(URL).param("page", "abc").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
        when(service.myReturnedBooks(12L, 0)).thenReturn(new com.duanttcsn5.library.dto.loan.MyReturnedBooksPageResponse(
                java.util.List.of(), 0, 20, 0));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }
}
