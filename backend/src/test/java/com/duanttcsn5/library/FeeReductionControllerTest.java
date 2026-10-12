package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.FeeReductionController;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.FeeReductionService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeeReductionControllerTest {
    private static final String URL = "/api/v1/overdue-fees/5/reduction-requests";
    private static final String PENDING = "/api/v1/overdue-fees/reduction-requests/pending";
    private static final String BODY = "{\"amountVnd\":10000,\"reason\":\"Bạn đọc có hoàn cảnh khó khăn\"}";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, FeeReductionController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean FeeReductionService service() { return mock(FeeReductionService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
    private AnnotationConfigWebApplicationContext context;
    private FeeReductionService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(FeeReductionService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    private void token(String roleCode) {
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("12").claim("tokenVersion", 0).build());
        User user = new User(); user.setId(12L); user.setStatus("ACTIVE"); user.setFullName("Thủ thư An");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder call(String body) {
        return post(URL).header("Authorization", "Bearer test-token").contentType("application/json").content(body);
    }
    private FeeReductionRequestResponse saved(long amount) {
        return new FeeReductionRequestResponse(2L, 5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc", "LIB-001",
                12L, "Thủ thư An", 15000, amount, 15000, "Bạn đọc có hoàn cảnh khó khăn",
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00"), "PENDING");
    }
    @Test void anonymousAndInvalidJwtReturn401() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("bad"));
        mvc.perform(post(URL).header("Authorization", "Bearer bad").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void onlyLibrarianCanPostEvenWhenOtherRolesCallApiDirectly() throws Exception {
        for (String role : new String[]{"READER", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role); mvc.perform(call(BODY)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void partialAndFullRequestsReturn201AndUseActorFromJwt() throws Exception {
        token("LIBRARIAN");
        for (long amount : new long[]{10000, 15000}) {
            var request = new CreateFeeReductionRequest(BigDecimal.valueOf(amount), "Bạn đọc có hoàn cảnh khó khăn");
            when(service.create(5L, request, 12L)).thenReturn(saved(amount));
            mvc.perform(call("{\"amountVnd\":" + amount + ",\"reason\":\"Bạn đọc có hoàn cảnh khó khăn\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.outstandingAmountVnd").value(15000))
                    .andExpect(jsonPath("$.requestedAmountVnd").value(amount))
                    .andExpect(jsonPath("$.requestedBy").value(12));
        }
    }
    @Test void blankReasonsMissingAmountZeroNegativeAndFractionFailValidation() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{"{}", "{\"amountVnd\":10000}",
                "{\"amountVnd\":10000,\"reason\":\"\"}", "{\"amountVnd\":10000,\"reason\":\"  \"}",
                "{\"amountVnd\":0,\"reason\":\"Lý do\"}", "{\"amountVnd\":-1,\"reason\":\"Lý do\"}",
                "{\"amountVnd\":0.5,\"reason\":\"Lý do\"}"}) {
            mvc.perform(call(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
    @Test void existingErrorContractReportsExcessAmountAndPendingConflict() throws Exception {
        token("LIBRARIAN"); var request = new CreateFeeReductionRequest(new BigDecimal("10000"), "Bạn đọc có hoàn cảnh khó khăn");
        doThrow(new ApiException(HttpStatus.CONFLICT, "FEE_REDUCTION_PENDING", "Đề nghị đang chờ duyệt."))
                .when(service).create(5L, request, 12L);
        mvc.perform(call(BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEE_REDUCTION_PENDING"));
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDUCTION_AMOUNT", "Số tiền vượt phí gốc."))
                .when(service).create(5L, request, 12L);
        mvc.perform(call(BODY)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REDUCTION_AMOUNT"));
    }
    @Test void pendingListIsManagerOnlyAndUsesExistingResponseContract() throws Exception {
        for (String role : new String[]{"READER", "LIBRARIAN", "ADMIN"}) {
            token(role); mvc.perform(get(PENDING).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
        token("LIBRARY_MANAGER");
        when(service.pending(0, 12L)).thenReturn(new FeeReductionRequestPageResponse(List.of(saved(10000)), 0, 20, 1, 1));
        mvc.perform(get(PENDING).header("Authorization", "Bearer test-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("Bạn đọc có hoàn cảnh khó khăn"))
                .andExpect(jsonPath("$.items[0].outstandingAmountVnd").value(15000));
    }
    @Test void readerCannotReadFeeDetails() throws Exception {
        token("READER"); mvc.perform(get("/api/v1/overdue-fees/5").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
}
