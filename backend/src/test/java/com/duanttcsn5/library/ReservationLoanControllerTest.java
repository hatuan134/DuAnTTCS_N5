package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.ReservationLoanResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanContextResponse;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationLoanControllerTest {
    private static final String URL = "/api/v1/reservations/21/loan";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LoanService reservations() { return mock(LoanService.class); }
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(String json) {
        return post(URL).header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void missingAndInvalidJwtCannotConvertOrReadCard() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"cardNumber\":\"TV-0012\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reservations/21/loan-context")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(post(URL).header("Authorization", "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardNumber\":\"TV-0012\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void readerCannotConvertOrReadAnotherReadersCard() throws Exception {
        token("READER");
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void existingStaffRolesCanCreateAndReadContext() throws Exception {
        var result = new ReservationLoanResponse(81L, "PM-DEMO", 21L, 99L, "Nguyễn Văn An", "TV-0012",
                7L, "Mắt biếc", 31L, "LIB-031", OffsetDateTime.now(), "Đã lập phiếu mượn thành công.");
        when(service.createFromReservation(21L, 12L, "TV-0012")).thenReturn(result);
        when(service.pickupContext(21L)).thenReturn(new ReservationLoanContextResponse("TV-0012", false, null));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(81))
                    .andExpect(jsonPath("$.readerId").value(99)).andExpect(jsonPath("$.barcode").value("LIB-031"))
                    .andExpect(jsonPath("$.loanNumber").value("PM-DEMO"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
            mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.cardNumber").value("TV-0012"));
        }
    }

    @Test
    void missingNullBlankAndOversizeCardAreValidatedBeforeService() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{"{}", "{\"cardNumber\":null}", "{\"cardNumber\":\"\"}",
                "{\"cardNumber\":\"   \"}", "{\"cardNumber\":\"" + "x".repeat(101) + "\"}"}) {
            mvc.perform(submit(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void mismatchAndRepeatedConversionUseVietnameseDomainErrors() throws Exception {
        token("LIBRARIAN");
        when(service.createFromReservation(21L, 12L, "WRONG")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "RESERVATION_CARD_MISMATCH", "Mã thẻ không đúng với bạn đọc sở hữu đơn đặt giữ."));
        mvc.perform(submit("{\"cardNumber\":\"WRONG\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_CARD_MISMATCH"));
        when(service.createFromReservation(21L, 12L, "TV-0012")).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_ALREADY_CONVERTED", "Đơn đã chuyển thành phiếu mượn."));
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_CONVERTED"));
    }
}
