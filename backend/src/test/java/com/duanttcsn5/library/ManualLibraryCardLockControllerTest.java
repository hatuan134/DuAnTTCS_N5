package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LibraryCardController;
import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.dto.book.BookReservationBatchResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.LibraryCardService;
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
import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManualLibraryCardLockControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LibraryCardController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LibraryCardService reservations() { return mock(LibraryCardService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    AnnotationConfigWebApplicationContext context;
    LibraryCardService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(LibraryCardService.class);
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/v1/library-cards/5/lock").contentType(MediaType.APPLICATION_JSON).content(body);
    }
    @Test void guestCannotLock() throws Exception {
        mvc.perform(request("{\"reason\":\"Vi phạm\"}" )).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void onlyLibraryManagerCanLockIncludingAdminDenied() throws Exception {
        for (String role : new String[]{"READER", "LIBRARIAN", "ADMIN"}) {
            token(role);
            mvc.perform(request("{\"reason\":\"Vi phạm\"}").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void blankOrMalformedRequestDoesNotReachService() throws Exception {
        token("LIBRARY_MANAGER");
        for (String body : new String[]{"{}", "{\"reason\":\"   \"}",
                "{\"reason\":\"Vi phạm\",\"endsAt\":\"2026-02-30T12:00:00+07:00\"}"}) {
            mvc.perform(request(body).header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void managerIdentityComesFromJwtAndServerReceivesDuration() throws Exception {
        token("LIBRARY_MANAGER");
        mvc.perform(request("{\"reason\":\"Vi phạm\",\"endsAt\":\"2026-10-21T10:00:00+07:00\",\"actorId\":999}")
                .header("Authorization", "Bearer test-token")).andExpect(status().isOk());
        verify(service).lockCard(eq(5L), argThat(r -> "Vi phạm".equals(r.reason()) && r.endsAt() != null), eq(12L), anyString());
    }
}
