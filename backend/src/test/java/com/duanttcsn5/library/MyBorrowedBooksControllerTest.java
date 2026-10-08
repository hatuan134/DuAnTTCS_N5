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

class MyBorrowedBooksControllerTest {
    private static final String URL = "/api/v1/loans/me/borrowed-books";
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


    @Test void anonymousAndInvalidJwtDenied() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(get(URL).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void staffDenied() throws Exception {
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void readerUsesOnlyJwtIdentityEvenIfOtherIdIsSupplied() throws Exception {
        token("READER");
        when(service.myBorrowedBooks(12L)).thenReturn(java.util.List.of(
                new com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse(1L, "Mắt biếc", "LIB-1",
                        java.time.OffsetDateTime.parse("2026-10-07T10:00:00Z"),
                        java.time.OffsetDateTime.parse("2026-10-08T10:00:00Z"), 0L, 0, 2)));
        mvc.perform(get(URL).param("readerId", "99").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bookTitle").value("Mắt biếc"))
                .andExpect(jsonPath("$[0].barcode").value("LIB-1"))
                .andExpect(jsonPath("$[0].remainingDays").value(0))
                .andExpect(jsonPath("$[0].renewalsUsed").value(0))
                .andExpect(jsonPath("$[0].maxRenewals").value(2));
        verify(service).myBorrowedBooks(12L);
        verifyNoMoreInteractions(service);
    }
    @Test void readerWithoutLoansGetsEmptyArray() throws Exception {
        token("READER"); when(service.myBorrowedBooks(12L)).thenReturn(java.util.List.of());
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
