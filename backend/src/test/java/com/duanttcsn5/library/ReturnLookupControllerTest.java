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

class ReturnLookupControllerTest {
    private static final String URL = "/api/v1/loans/return-lookup";
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
    @Test void readerDeniedWithoutReadingBorrowerData() throws Exception {
        token("READER");
        mvc.perform(get(URL).param("barcode", "LIB-001").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffRolesReceiveOpenItemFields() throws Exception {
        when(service.lookupReturn("LIB-001", 12L)).thenReturn(new com.duanttcsn5.library.dto.loan.ReturnLookupResponse(
                "OVERDUE", "Sách quá hạn 1 ngày.", 4L, "LIB-001", "Mắt biếc", 8L, "PM-008", 9L,
                20L, "Nguyễn An", java.time.OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                java.time.OffsetDateTime.parse("2026-10-08T10:00:00Z"), LocalDate.of(2026, 10, 9), 1L));
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(get(URL).param("barcode", "LIB-001").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.readerName").value("Nguyễn An"))
                    .andExpect(jsonPath("$.bookTitle").value("Mắt biếc"))
                    .andExpect(jsonPath("$.overdueDays").value(1)).andExpect(jsonPath("$.status").value("OVERDUE"));
        }
    }
    @Test void invalidBarcodeAndMissingBarcodeUseExistingErrorContract() throws Exception {
        token("LIBRARIAN");
        when(service.lookupReturn("", 12L)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BARCODE", "Vui lòng nhập mã vạch."));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BARCODE"));
        when(service.lookupReturn("UNKNOWN", 12L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "COPY_BARCODE_NOT_FOUND", "Mã vạch này không tồn tại trong thư viện."));
        mvc.perform(get(URL).param("barcode", "UNKNOWN").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COPY_BARCODE_NOT_FOUND"));
    }
}
