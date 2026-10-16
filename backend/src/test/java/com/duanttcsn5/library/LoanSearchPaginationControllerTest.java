package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.LoanSearchPageResponse;
import com.duanttcsn5.library.dto.loan.LoanSearchResultResponse;
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

class LoanSearchPaginationControllerTest {
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
        context.register(Config.class);
        context.refresh();
        service = context.getBean(LoanService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void cleanup() { context.close(); }

    private void token(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);
        User user = new User(); user.setId(12L); user.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @Test
    void staffSeesTotalAndTwentyRowPagingWithCodeRetained() throws Exception {
        var one = new LoanSearchResultResponse(99L, "PM-0099", "TV-0001", "Nguyễn An",
                OffsetDateTime.parse("2026-10-01T08:00:00+07:00"), "BORROWED", List.of());
        when(service.searchLoans("TV-0001", 0, 12L))
                .thenReturn(new LoanSearchPageResponse(List.of(one), 0, 20, 21));
        when(service.searchLoans("TV-0001", 1, 12L))
                .thenReturn(new LoanSearchPageResponse(List.of(one), 1, 20, 21));
        token("LIBRARIAN");
        for (int page = 0; page <= 1; page++) {
            mvc.perform(get("/api/v1/loans/search").param("code", "TV-0001")
                            .param("page", String.valueOf(page))
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.total").value(21))
                    .andExpect(jsonPath("$.items[0].status").value("BORROWED"));
        }
        verify(service).searchLoans("TV-0001", 0, 12L);
        verify(service).searchLoans("TV-0001", 1, 12L);
    }

    @Test
    void aMissingCodeReturnsAnActionableReasonWithoutChangingTheResponseShape() throws Exception {
        token("LIBRARIAN");
        when(service.searchLoans("TV-DOES-NOT-EXIST", 0, 12L))
                .thenReturn(new LoanSearchPageResponse(List.of(), 0, 20, 0,
                        "CODE_NOT_FOUND"));
        mvc.perform(get("/api/v1/loans/search").param("code", "TV-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.emptyReason").value("CODE_NOT_FOUND"));
    }

    @Test
    void defaultsToFirstPageAndRejectsInvalidPageViaExistingErrors() throws Exception {
        token("ADMIN");
        when(service.searchLoans("TV-0001", 0, 12L))
                .thenReturn(new LoanSearchPageResponse(List.of(), 0, 20, 0));
        mvc.perform(get("/api/v1/loans/search").param("code", "TV-0001")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total").value(0));
        when(service.searchLoans("TV-0001", -1, 12L))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                        "INVALID_LOAN_SEARCH_PAGE", "Số trang tra cứu phải lớn hơn hoặc bằng 0."));
        mvc.perform(get("/api/v1/loans/search").param("code", "TV-0001").param("page", "-1")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOAN_SEARCH_PAGE"));
        mvc.perform(get("/api/v1/loans/search").param("code", "TV-0001").param("page", "abc")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void readerAndAnonymousCannotSearchButStaffCan() throws Exception {
        mvc.perform(get("/api/v1/loans/search").param("code", "PM-1"))
                .andExpect(status().isUnauthorized());
        token("READER");
        mvc.perform(get("/api/v1/loans/search").param("code", "PM-1")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        for (String role : List.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")) {
            token(role);
            when(service.searchLoans("PM-1", 0, 12L))
                    .thenReturn(new LoanSearchPageResponse(List.of(), 0, 20, 0));
            mvc.perform(get("/api/v1/loans/search").param("code", "PM-1")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void combinedFiltersReachServiceAndPreservePagination() throws Exception {
        token("LIBRARIAN");
        when(service.searchLoans("CARD-01", 2, 12L, "2026-09-01", "2026-10-01", "RETURNED"))
                .thenReturn(new LoanSearchPageResponse(List.of(), 2, 20, 44));
        mvc.perform(get("/api/v1/loans/search").param("code", "CARD-01")
                        .param("page", "2").param("fromDate", "2026-09-01")
                        .param("toDate", "2026-10-01").param("status", "RETURNED")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(44))
                .andExpect(jsonPath("$.page").value(2));
        verify(service).searchLoans("CARD-01", 2, 12L, "2026-09-01", "2026-10-01", "RETURNED");
    }

    @Test
    void readerCannotBypassPermissionUsingFilters() throws Exception {
        token("READER");
        mvc.perform(get("/api/v1/loans/search")
                        .param("code", "PM-01").param("status", "RETURNED")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
