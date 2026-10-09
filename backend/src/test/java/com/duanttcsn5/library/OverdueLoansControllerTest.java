package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.OverdueLoanItemResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.LoanService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class OverdueLoansControllerTest {
    private static final String URL = "/api/v1/loans/overdue";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
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
        context.register(Config.class);
        context.refresh();
        service = context.getBean(LoanService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void token(String roleCode) {
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(token);
        User user = new User();
        user.setId(12L);
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode(roleCode);
        user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @Test
    void anonymousAndReaderAreDenied() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);

        token("READER");
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void staffReceivesRequiredFieldsAndOrderingFromService() throws Exception {
        when(service.overdueLoans(12L)).thenReturn(List.of(
                new OverdueLoanItemResponse(1L, "PM-001", 11L, 20L,
                        "Nguyễn An", "0901234567", 30L, "Mắt biếc",
                        OffsetDateTime.parse("2026-10-05T10:00:00+07:00"), 4L),
                new OverdueLoanItemResponse(2L, "PM-002", 22L, 21L,
                        "Trần Bình", "0912345678", 31L, "Dế Mèn phiêu lưu ký",
                        OffsetDateTime.parse("2026-10-08T10:00:00+07:00"), 1L)
        ));

        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].readerName").value("Nguyễn An"))
                    .andExpect(jsonPath("$[0].readerPhone").value("0901234567"))
                    .andExpect(jsonPath("$[0].bookTitle").value("Mắt biếc"))
                    .andExpect(jsonPath("$[0].overdueDays").value(4))
                    .andExpect(jsonPath("$[1].overdueDays").value(1));
        }
    }
}
