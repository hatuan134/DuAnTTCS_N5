package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReadyPickupControllerTest {
    private static final String URL = "/api/v1/reservations/ready-for-pickup";

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

    private ReadyForPickupReservationResponse row(long id, String barcode, String reader, String deadline) {
        return new ReadyForPickupReservationResponse(id, 7L, "Mắt biếc", id + 100, barcode,
                99L, reader, "READY_FOR_PICKUP", OffsetDateTime.parse("2026-10-03T08:00:00+07:00"),
                OffsetDateTime.parse(deadline));
    }

    @Test
    void guestsAndInvalidTokensCannotReadEitherEndpoint() throws Exception {
        for (String path : new String[]{URL, URL + "/21"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
            mvc.perform(get(path).header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void readerCannotReadListOrOtherReadersDetails() throws Exception {
        token("READER");
        mvc.perform(get(URL).header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/21").header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void allExistingStaffRolesCanReadEmptyListAndDetail() throws Exception {
        when(service.getReadyForPickup()).thenReturn(List.of());
        when(service.getReadyForPickupById(21L)).thenReturn(row(21L, "LIB-021", "Nguyễn Văn An", "2026-10-06T17:00:00+07:00"));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(get(URL + "/21").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(21));
        }
    }

    @Test
    void listContainsCorrectValuesAndDoesNotExposeAccountFields() throws Exception {
        token("LIBRARIAN");
        when(service.getReadyForPickup()).thenReturn(List.of(
                row(22L, "LIB-022", "Trần Bình", "2026-10-05T12:00:00+07:00"),
                row(21L, "LIB-021", "Nguyễn Văn An", "2026-10-06T17:00:00+07:00")));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(22))
                .andExpect(jsonPath("$[0].bookTitle").value("Mắt biếc"))
                .andExpect(jsonPath("$[0].barcode").value("LIB-022"))
                .andExpect(jsonPath("$[0].readerName").value("Trần Bình"))
                .andExpect(jsonPath("$[1].id").value(21))
                .andExpect(jsonPath("$[1].barcode").value("LIB-021"))
                .andExpect(jsonPath("$[1].readerName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].pickupDeadline").exists())
                .andExpect(jsonPath("$[0].status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].reader").doesNotExist());
    }

    @Test
    void detailMatchesSelectedReservationWithoutSensitiveFields() throws Exception {
        token("LIBRARIAN");
        when(service.getReadyForPickupById(21L)).thenReturn(row(21L, "LIB-021", "Nguyễn Văn An", "2026-10-06T17:00:00+07:00"));
        mvc.perform(get(URL + "/21").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(7))
                .andExpect(jsonPath("$.barcode").value("LIB-021"))
                .andExpect(jsonPath("$.readerName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service).getReadyForPickupById(21L);
    }

    @Test
    void invalidPathAndMissingOrChangedOrderUseExistingErrors() throws Exception {
        token("LIBRARIAN");
        mvc.perform(get(URL + "/abc").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
        when(service.getReadyForPickupById(0L)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_RESERVATION_ID", "Mã đơn đặt giữ không hợp lệ."));
        mvc.perform(get(URL + "/0").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESERVATION_ID"));
        when(service.getReadyForPickupById(21L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "READY_RESERVATION_NOT_FOUND", "Đơn có thể đã đổi trạng thái."));
        mvc.perform(get(URL + "/21").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("READY_RESERVATION_NOT_FOUND"));
    }
}
