package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.ReaderController;
import com.duanttcsn5.library.dto.reader.ReaderLoanHistoryResponse;
import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.duanttcsn5.library.service.ReaderSelfService;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.ReaderRegistrationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class ReaderLoanHistoryControllerTest {
    private static final String URL = "/api/v1/readers/20/loan-history";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ReaderController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean ReaderRegistrationService readers() { return mock(ReaderRegistrationService.class); }
        @Bean ReaderSelfService selfService() { return mock(ReaderSelfService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private ReaderRegistrationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(ReaderRegistrationService.class);
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
    void onlyLibrarianAndManagerReceiveSummaryAndHistory() throws Exception {
        when(service.getReaderLoanHistory(20L)).thenReturn(
                new ReaderLoanHistoryResponse(null, 1, 2, 1, List.of()));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openLoanCount").value(1))
                    .andExpect(jsonPath("$.totalBorrowCount").value(2))
                    .andExpect(jsonPath("$.lateReturnCount").value(1))
                    .andExpect(jsonPath("$.loans").isArray());
        }
    }

    @Test
    void invalidAndUnknownReadersUseExistingErrorContract() throws Exception {
        token("LIBRARIAN");
        when(service.getReaderLoanHistory(0L)).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, "INVALID_READER_ID", "Mã bạn đọc không hợp lệ."));
        mvc.perform(get("/api/v1/readers/0/loan-history").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_READER_ID"));
        when(service.getReaderLoanHistory(999L)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "READER_NOT_FOUND", "Không tìm thấy hồ sơ bạn đọc."));
        mvc.perform(get("/api/v1/readers/999/loan-history").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("READER_NOT_FOUND"));
        mvc.perform(get("/api/v1/readers/abc/loan-history").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void authorizedRolesReceiveFilteredHistoryWithSameResponseShape() throws Exception {
        when(service.getReaderLoanHistory(20L, "2026-10-01", "2026-10-09"))
                .thenReturn(new ReaderLoanHistoryResponse(null, 2, 4, 1, List.of()));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).param("fromDate", "2026-10-01").param("toDate", "2026-10-09")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalBorrowCount").value(4))
                    .andExpect(jsonPath("$.openLoanCount").value(2))
                    .andExpect(jsonPath("$.lateReturnCount").value(1)).andExpect(jsonPath("$.loans").isArray());
        }
    }

    @Test
    void acceptsStartOnlyAndEndOnly() throws Exception {
        token("LIBRARIAN");
        when(service.getReaderLoanHistory(20L, "2026-10-01", null))
                .thenReturn(new ReaderLoanHistoryResponse(null, 1, 2, 0, List.of()));
        when(service.getReaderLoanHistory(20L, null, "2026-10-09"))
                .thenReturn(new ReaderLoanHistoryResponse(null, 1, 2, 0, List.of()));
        mvc.perform(get(URL).param("fromDate", "2026-10-01").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        mvc.perform(get(URL).param("toDate", "2026-10-09").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidDateFilterReturnsVietnameseApiError() throws Exception {
        token("LIBRARIAN");
        when(service.getReaderLoanHistory(20L, "2026-10-09", "2026-10-01"))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_READER_HISTORY_DATE_RANGE",
                        "Từ ngày không được lớn hơn Đến ngày."));
        mvc.perform(get(URL).param("fromDate", "2026-10-09").param("toDate", "2026-10-01")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_READER_HISTORY_DATE_RANGE"))
                .andExpect(jsonPath("$.message").value("Từ ngày không được lớn hơn Đến ngày."));
    }

    @Test
    void deniedRolesReceiveNoHistoryForAnyFilterOrReaderId() throws Exception {
        for (String role : new String[]{"READER", "AUDITOR", "UNKNOWN"}) {
            token(role);
            for (String readerId : new String[]{"20", "999", "0", "abc"}) {
                for (String query : new String[]{"", "?fromDate=2026-10-01", "?toDate=2026-10-09",
                        "?fromDate=2026-10-01&toDate=2026-10-09", "?fromDate=invalid"}) {
                    mvc.perform(get("/api/v1/readers/" + readerId + "/loan-history" + query)
                                    .header("Authorization", "Bearer test-token"))
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                            .andExpect(jsonPath("$.profile").doesNotExist())
                            .andExpect(jsonPath("$.openLoanCount").doesNotExist())
                            .andExpect(jsonPath("$.totalBorrowCount").doesNotExist())
                            .andExpect(jsonPath("$.lateReturnCount").doesNotExist())
                            .andExpect(jsonPath("$.loans").doesNotExist())
                            .andExpect(jsonPath("$.details.*").doesNotExist());
                }
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousReceivesNoHistoryWithOrWithoutFilters() throws Exception {
        for (String query : new String[]{"", "?fromDate=2026-10-01&toDate=2026-10-09"}) {
            mvc.perform(get(URL + query)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.profile").doesNotExist())
                    .andExpect(jsonPath("$.openLoanCount").doesNotExist())
                    .andExpect(jsonPath("$.totalBorrowCount").doesNotExist())
                    .andExpect(jsonPath("$.lateReturnCount").doesNotExist())
                    .andExpect(jsonPath("$.loans").doesNotExist());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminCanStillReadBasicProfileAndList() throws Exception {
        token("ADMIN");
        when(service.getAllReaders()).thenReturn(List.of());
        mvc.perform(get("/api/v1/readers").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/readers/20").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).getAllReaders();
        org.mockito.Mockito.verify(service).getReaderById(20L);
    }

    @Test
    void readerCannotBypassStaffAuthorizationWithDateParameters() throws Exception {
        token("READER");
        mvc.perform(get(URL).param("fromDate", "2026-10-01").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
