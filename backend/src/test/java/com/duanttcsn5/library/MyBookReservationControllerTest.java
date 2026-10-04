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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyBookReservationControllerTest {
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

    @Test void guestAndInvalidTokenGet401() throws Exception {
        mvc.perform(get("/api/v1/reservations/mine")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(get("/api/v1/reservations/mine").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void staffRolesGet403() throws Exception {
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(get("/api/v1/reservations/mine").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void identityAlwaysComesFromJwtAndResponseContainsNoOtherReaderData() throws Exception {
        token("READER");
        when(service.getMyReservations(12L)).thenReturn(List.of(new MyBookReservationResponse(
                100L, 7L, "Lập trình Java", "PENDING", OffsetDateTime.parse("2030-01-01T08:00:00+07:00"),
                3L, null)));
        mvc.perform(get("/api/v1/reservations/mine").param("readerId", "999")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100)).andExpect(jsonPath("$[0].bookTitle").value("Lập trình Java"))
                .andExpect(jsonPath("$[0].queuePosition").value(3)).andExpect(jsonPath("$[0].reservedAt").exists())
                .andExpect(jsonPath("$[0].pickupDeadline").doesNotExist())
                .andExpect(jsonPath("$[0].readerId").doesNotExist())
                .andExpect(jsonPath("$[0].readerName").doesNotExist());
        verify(service).getMyReservations(12L);
        verify(service, never()).getMyReservations(999L);
    }

    @Test void readerWithoutOrdersGetsEmptyArray() throws Exception {
        token("READER"); when(service.getMyReservations(12L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/reservations/mine").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
