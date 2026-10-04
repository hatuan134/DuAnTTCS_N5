package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.MyBookReservationResponse;
import java.util.List;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
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
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReaderReservationCancellationControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, BookReservationController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean BookReservationService reservations() { return mock(BookReservationService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    AnnotationConfigWebApplicationContext context;
    BookReservationService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(BookReservationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    private void token(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);
        User reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); reader.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(reader));
    }

    @Test void guestAndWrongRolesCannotCancel() throws Exception {
        mvc.perform(post("/api/v1/reservations/mine/100/cancel")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(post("/api/v1/reservations/mine/100/cancel").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void usesJwtIdentityAndReturnsNoContent() throws Exception {
        token("READER");
        mvc.perform(post("/api/v1/reservations/mine/100/cancel").param("readerId", "999")
                .header("Authorization", "Bearer test-token")).andExpect(status().isNoContent());
        verify(service).cancelMine(100L, 12L);
        verify(service, never()).cancelMine(100L, 999L);
    }

    @Test void directCancelRequestForBorrowedReservationReturnsSpecificConflictReason() throws Exception {
        token("READER");
        doThrow(new com.duanttcsn5.library.exception.ApiException(
                org.springframework.http.HttpStatus.CONFLICT,
                "RESERVATION_ALREADY_BORROWED",
                "Không thể huỷ đơn vì sách đã được nhận và đơn đã chuyển thành phiếu mượn."))
                .when(service).cancelMine(100L, 12L);

        mvc.perform(post("/api/v1/reservations/mine/100/cancel")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_BORROWED"))
                .andExpect(jsonPath("$.message").value(
                        "Không thể huỷ đơn vì sách đã được nhận và đơn đã chuyển thành phiếu mượn."));
    }

    @Test void propagatesOwnershipAndStaleStateErrors() throws Exception {
        token("READER");
        for (var code : new org.springframework.http.HttpStatus[]{
                org.springframework.http.HttpStatus.NOT_FOUND, org.springframework.http.HttpStatus.CONFLICT}) {
            doThrow(new com.duanttcsn5.library.exception.ApiException(code, "CANCEL_ERROR", "Không thể huỷ đơn."))
                    .when(service).cancelMine(100L, 12L);
            mvc.perform(post("/api/v1/reservations/mine/100/cancel").header("Authorization", "Bearer test-token"))
                    .andExpect(status().is(code.value())).andExpect(jsonPath("$.message").value("Không thể huỷ đơn."));
        }
    }
}
