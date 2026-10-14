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

class CardLockSuggestionControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LibraryCardController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean com.duanttcsn5.library.service.CardLockSuggestionService suggestions() { return mock(com.duanttcsn5.library.service.CardLockSuggestionService.class); }
        @Bean LibraryCardService reservations() { return mock(LibraryCardService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    AnnotationConfigWebApplicationContext context;
    com.duanttcsn5.library.service.CardLockSuggestionService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(com.duanttcsn5.library.service.CardLockSuggestionService.class);
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


    @Test void allSuggestionOperationsDenyGuestsAndOtherRoles() throws Exception {
        for(String role: new String[]{"READER","LIBRARIAN","ADMIN"}) {
            token(role);
            String[] paths={"/lock-suggestions","/lock-suggestions/settings","/lock-suggestions/5"};
            for(String path:paths) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/library-cards"+path).header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/library-cards/lock-suggestions/5/accept").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Vi phạm\",\"endsAt\":null}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/library-cards/lock-suggestions/5/ignore").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Xem xét sau\"}")).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/library-cards/lock-suggestions/settings").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"debtThresholdVnd\":100000}")).andExpect(status().isForbidden());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/library-cards/lock-suggestions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void managerCanReadSettingsAndList() throws Exception {
        token("LIBRARY_MANAGER");
        when(service.settings()).thenReturn(new com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Settings(100000,OffsetDateTime.now(),12L,null));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/library-cards/lock-suggestions/settings").header("Authorization","Bearer test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.debtThresholdVnd").value(100000));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/library-cards/lock-suggestions?history=true&page=2&size=20").header("Authorization","Bearer test-token")).andExpect(status().isOk());
        verify(service).list(true,2,20);
    }
    @Test void invalidThresholdAndMissingDecisionReasonAreRejected() throws Exception {
        token("LIBRARY_MANAGER");
        for(String amount:new String[]{"null","-1","0.5","1000000000001"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/library-cards/lock-suggestions/settings").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"debtThresholdVnd\":"+amount+"}")).andExpect(status().isBadRequest());
        }
        for(String action:new String[]{"accept","ignore"}) mvc.perform(post("/api/v1/library-cards/lock-suggestions/5/"+action).header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void managerDecisionUsesAuthenticatedActorId() throws Exception {
        token("LIBRARY_MANAGER");
        mvc.perform(post("/api/v1/library-cards/lock-suggestions/5/ignore").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Theo dõi thêm\"}")).andExpect(status().isOk());
        verify(service).ignore(eq(5L),any(),eq(12L));
        mvc.perform(post("/api/v1/library-cards/lock-suggestions/5/accept").header("Authorization","Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Quá hạn\",\"endsAt\":null}")).andExpect(status().isOk());
        verify(service).accept(eq(5L),any(),eq(12L),anyString());
    }
}
