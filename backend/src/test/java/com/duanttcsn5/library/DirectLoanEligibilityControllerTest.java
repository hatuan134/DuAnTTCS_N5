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

class DirectLoanEligibilityControllerTest {
    private static final String URL = "/api/v1/loans/reader-eligibility";
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


    @Test void anonymousInvalidJwtAndReaderCannotReadBorrowerDetails() throws Exception {
        mvc.perform(get(URL).param("cardNumber", "TV-0012")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(get(URL).param("cardNumber", "TV-0012").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
        token("READER");
        mvc.perform(get(URL).param("cardNumber", "TV-0012").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void existingStaffRolesReadOnlyTheRequiredSnapshot() throws Exception {
        when(service.readerEligibility("TV-0012", 12L)).thenReturn(new ReaderLoanEligibilityResponse(
                99L, "Nguyễn Văn An", "TV-0012", "Thẻ sinh viên", "ACTIVE",
                LocalDate.of(2026, 12, 31), 5, 2L, 3L, true, "ELIGIBLE", "Đủ điều kiện mượn."));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).param("cardNumber", "TV-0012").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.readerName").value("Nguyễn Văn An"))
                    .andExpect(jsonPath("$.cardTypeName").value("Thẻ sinh viên"))
                    .andExpect(jsonPath("$.borrowedBooks").value(2)).andExpect(jsonPath("$.remainingBooks").value(3))
                    .andExpect(jsonPath("$.eligible").value(true))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        }
    }

    @Test void unknownCardAndBlankRequestUseVietnameseDomainErrors() throws Exception {
        token("LIBRARIAN");
        when(service.readerEligibility("UNKNOWN", 12L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "LIBRARY_CARD_NOT_FOUND", "Không tìm thấy bạn đọc với mã thẻ này."));
        mvc.perform(get(URL).param("cardNumber", "UNKNOWN").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LIBRARY_CARD_NOT_FOUND"));
        when(service.readerEligibility("", 12L)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_CARD_NUMBER", "Vui lòng nhập mã thẻ từ 1 đến 100 ký tự."));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CARD_NUMBER"));
    }
}
