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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReturnConfirmationControllerTest {
    private static final String URL = "/api/v1/loans/return-confirmation";
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


    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder call(String payload) {
        return post(URL).contentType("application/json").content(payload);
    }
    private static final String BODY = "{\"barcode\":\"LIB-001\",\"itemId\":9}";
    @Test void anonymousAndInvalidJwtDenied() throws Exception {
        mvc.perform(call(BODY)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(call(BODY).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void readerDeniedBeforeService() throws Exception {
        token("READER"); mvc.perform(call(BODY).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void staffRolesUseAuthenticatedReceiverAndReturnSavedFields() throws Exception {
        var request = new com.duanttcsn5.library.dto.loan.ConfirmReturnRequest("LIB-001", 9L);
        when(service.confirmReturn(request, 12L)).thenReturn(new com.duanttcsn5.library.dto.loan.ConfirmReturnResponse(
                "Nhận trả sách thành công.", 4L, "LIB-001", "Mắt biếc", 8L, "PM-008", 9L,
                "RETURNED", "RETURNED", "AVAILABLE", java.time.OffsetDateTime.parse("2026-10-09T01:00:00+07:00"),
                12L, "Thủ thư An"));
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role); mvc.perform(call(BODY).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.itemStatus").value("RETURNED"))
                    .andExpect(jsonPath("$.loanStatus").value("RETURNED"))
                    .andExpect(jsonPath("$.copyStatus").value("AVAILABLE"))
                    .andExpect(jsonPath("$.returnedById").value(12))
                    .andExpect(jsonPath("$.returnedAt").exists());
        }
    }
    @Test void invalidBodyUsesExistingValidationContract() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{"{}", "{\"barcode\":\"  \",\"itemId\":9}",
                "{\"barcode\":\"LIB-001\",\"itemId\":0}", "{\"barcode\":\"LIB-001\",\"itemId\":null}"}) {
            mvc.perform(call(body).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
    @Test void queueAllocationReturnsHeldCopyAndPickupDetailsForStaff() throws Exception {
        token("LIBRARIAN");
        var request = new com.duanttcsn5.library.dto.loan.ConfirmReturnRequest("LIB-001", 9L);
        var startedAt = java.time.OffsetDateTime.parse("2026-10-09T01:00:00+07:00");
        when(service.confirmReturn(request, 12L)).thenReturn(new com.duanttcsn5.library.dto.loan.ConfirmReturnResponse(
                "Nhận trả sách thành công. Bản sao được giữ cho người đầu hàng đợi.",
                4L, "LIB-001", "Mắt biếc", 8L, "PM-008", 9L, "RETURNED", "RETURNED", "HELD",
                startedAt, 12L, "Thủ thư An", 30L, "Bạn đọc Bình", startedAt, startedAt.plusDays(3)));
        mvc.perform(call(BODY).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.copyStatus").value("HELD"))
                .andExpect(jsonPath("$.nextReservationId").value(30))
                .andExpect(jsonPath("$.nextReaderName").value("Bạn đọc Bình"))
                .andExpect(jsonPath("$.holdStartedAt").exists())
                .andExpect(jsonPath("$.pickupDeadline").exists());
    }
    @Test void repeatedReturnUsesConflictAndRollbackErrorUsesExistingErrorResponse() throws Exception {
        token("LIBRARIAN"); var request = new com.duanttcsn5.library.dto.loan.ConfirmReturnRequest("LIB-001", 9L);
        when(service.confirmReturn(request, 12L)).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "LOAN_ALREADY_RETURNED", "Cuốn sách này đã được nhận trả."));
        mvc.perform(call(BODY).header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
        // The mock already throws for this invocation; when(...) would execute that stub.
        doThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                "RETURN_SAVE_FAILED", "Dữ liệu được giữ nguyên.")).when(service).confirmReturn(request, 12L);
        mvc.perform(call(BODY).header("Authorization", "Bearer test-token"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("RETURN_SAVE_FAILED"));
    }
}
