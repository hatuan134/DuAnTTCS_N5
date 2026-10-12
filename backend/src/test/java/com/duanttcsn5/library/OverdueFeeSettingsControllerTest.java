package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LibraryConfigurationController;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OverdueFeeSettingsControllerTest {
    private static final String URL = "/api/v1/library-settings/overdue-fee";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LibraryConfigurationController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LibraryConfigurationService libraryConfiguration() { return mock(LibraryConfigurationService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private LibraryConfigurationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(LibraryConfigurationService.class);
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



    @Test void anonymousAndReaderCannotUpdatePrice() throws Exception {
        mvc.perform(put(URL).contentType("application/json").content("{\"dailyRateVnd\":5000}"))
                .andExpect(status().isUnauthorized());
        token("READER");
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType("application/json")
                .content("{\"dailyRateVnd\":5000}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void librarianCanReadButCannotChangePrice() throws Exception {
        token("LIBRARIAN");
        when(service.getOverdueFeeSetting()).thenReturn(new com.duanttcsn5.library.dto.libraryconfig.OverdueFeeSettingResponse(5000L, null, null));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyRateVnd").value(5000));
        mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType("application/json")
                .content("{\"dailyRateVnd\":5000}")).andExpect(status().isForbidden());
        verify(service, never()).updateOverdueFeeSetting(any(), any(), any());
    }
    @Test void managerAndAdminCanConfigure5000AndZero() throws Exception {
        when(service.updateOverdueFeeSetting(any(), eq(12L), any())).thenAnswer(call -> {
            var request = (com.duanttcsn5.library.dto.libraryconfig.OverdueFeeSettingRequest) call.getArgument(0);
            return new com.duanttcsn5.library.dto.libraryconfig.OverdueFeeSettingResponse(request.dailyRateVnd().longValueExact(), null, 12L);
        });
        for (String role : new String[]{"LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            for (int amount : new int[]{5000, 0}) mvc.perform(put(URL).header("Authorization", "Bearer test-token")
                    .contentType("application/json").content("{\"dailyRateVnd\":" + amount + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.dailyRateVnd").value(amount));
        }
    }
    @Test void missingNegativeDecimalAndOversizedRatesAreRejectedBeforeService() throws Exception {
        token("LIBRARY_MANAGER");
        for (String body : new String[]{"{}", "{\"dailyRateVnd\":null}", "{\"dailyRateVnd\":-1}",
                "{\"dailyRateVnd\":500.5}", "{\"dailyRateVnd\":1000000001}", "{\"dailyRateVnd\":\"abc\"}"}) {
            mvc.perform(put(URL).header("Authorization", "Bearer test-token").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
