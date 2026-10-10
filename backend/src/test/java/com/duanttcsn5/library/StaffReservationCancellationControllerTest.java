package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.CancelBookReservationResponse;
import com.duanttcsn5.library.dto.book.ReservationCancellationAuditResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffReservationCancellationControllerTest {
    private static final String URL = "/api/v1/reservations/21/cancel";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, BookReservationController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean BookReservationService reservations() { return mock(BookReservationService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private BookReservationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(BookReservationService.class);
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String reasonJson) {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":" + reasonJson + "}");
    }

    @Test
    void unauthenticatedInvalidTokenAndReaderCannotCancel() throws Exception {
        mvc.perform(request("\"Yêu cầu của bạn đọc\"")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(request("\"Yêu cầu của bạn đọc\"").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        token("READER");
        mvc.perform(request("\"Yêu cầu của bạn đọc\"").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void staffRolesCancelUsingPrincipalAndReceiveAuditAndCopyOutcome() throws Exception {
        var at = OffsetDateTime.parse("2026-10-03T14:00:00+07:00");
        when(service.cancelByStaff(21L, 12L, "Không đến nhận")).thenReturn(new CancelBookReservationResponse(
                21L, 7L, "CANCELLED", new ReservationCancellationAuditResponse(12L, "Thủ thư An", at, "Không đến nhận"),
                101L, "LIB-101", "TRANSFERRED", 23L, "Bạn đọc Chi", at.plusDays(3), "Đã huỷ đơn #21."));
        for (String role : new String[]{"LIBRARIAN", "ADMIN"}) {
            token(role);
            mvc.perform(request("\"Không đến nhận\"").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellation.actorId").value(12))
                    .andExpect(jsonPath("$.cancellation.actorName").value("Thủ thư An"))
                    .andExpect(jsonPath("$.cancellation.reason").value("Không đến nhận"))
                    .andExpect(jsonPath("$.cancellation.cancelledAt").exists())
                    .andExpect(jsonPath("$.copyOutcome").value("TRANSFERRED"))
                    .andExpect(jsonPath("$.nextReservationId").value(23))
                    .andExpect(jsonPath("$.barcode").value("LIB-101"))
                    .andExpect(jsonPath("$.email").doesNotExist());
        }
        verify(service, times(2)).cancelByStaff(21L, 12L, "Không đến nhận");
    }

    @Test
    void missingBlankAndOversizedReasonsFailValidationBeforeService() throws Exception {
        token("LIBRARIAN");
        for (String reason : new String[]{"null", "\"\"", "\"   \"", "\"" + "x".repeat(501) + "\""}) {
            mvc.perform(request(reason).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void changedStatusAndInvalidCopyReturnConflictWithVietnameseMessage() throws Exception {
        token("LIBRARIAN");
        when(service.cancelByStaff(21L, 12L, "Lý do huỷ")).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_NOT_CANCELLABLE", "Đơn đã đổi trạng thái. Không thể huỷ."));
        mvc.perform(request("\"Lý do huỷ\"").header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"))
                .andExpect(jsonPath("$.message").value("Đơn đã đổi trạng thái. Không thể huỷ."));
    }

    @Test
    void malformedIdAndMissingOrderUseExistingErrorResponses() throws Exception {
        token("LIBRARIAN");
        mvc.perform(post("/api/v1/reservations/abc/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Yêu cầu huỷ\"}").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
        when(service.cancelByStaff(21L, 12L, "Yêu cầu huỷ")).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND", "Không tìm thấy đơn đặt giữ."));
        mvc.perform(request("\"Yêu cầu huỷ\"").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }
}
