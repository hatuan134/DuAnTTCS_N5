package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.FeePaymentController;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.FeePaymentService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeePaymentControllerTest {
    static final String ROOT = "/api/v1/fee-payments";
    static final String BODY = "{\"requestKey\":\"e4c9cc1f-490c-4d15-9280-e45b7e50ab7e\",\"expectedTotalVnd\":30000,\"debtToken\":\"" + "a".repeat(64) + "\"}";
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, FeePaymentController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean FeePaymentService service() { return mock(FeePaymentService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc; FeePaymentService service;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh(); service = context.getBean(FeePaymentService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    User token(String code) {
        doReturn(Jwt.withTokenValue("test-token").header("alg", "HS256").subject("12").claim("tokenVersion", 0).build())
                .when(context.getBean(JwtService.class)).decode("test-token");
        Role role = new Role(); role.setCode(code); User user = new User(); user.setId(12L); user.setRole(role);
        user.setStatus("ACTIVE"); user.setFullName("Thủ thư An");
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user)); return user;
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder collect(String body) {
        return post(ROOT + "/readers/7/collect-full").header("Authorization", "Bearer test-token").contentType("application/json").content(body);
    }
    @Test void anonymousInvalidInactiveAndRevokedJwtCannotCollect() throws Exception {
        mvc.perform(post(ROOT + "/readers/7/collect-full").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        doThrow(new JwtException("bad")).when(context.getBean(JwtService.class)).decode("test-token");
        mvc.perform(collect(BODY)).andExpect(status().isUnauthorized());
        var user = token("LIBRARIAN"); user.setStatus("DISABLED"); mvc.perform(collect(BODY)).andExpect(status().isUnauthorized());
        user = token("LIBRARIAN"); user.setTokenVersion(1); mvc.perform(collect(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void nonLibrariansCannotSearchReadCollectOrReprint() throws Exception {
        for (String role : new String[]{"ADMIN", "LIBRARY_MANAGER", "READER"}) {
            token(role); mvc.perform(collect(BODY)).andExpect(status().isForbidden());
            mvc.perform(get(ROOT + "/readers").param("query", "THE").header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
            mvc.perform(get(ROOT + "/readers/7").header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
            mvc.perform(get(ROOT + "/9/receipt").header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void librarianUsesJwtIdentityAndReturnsFullReceipt() throws Exception {
        token("LIBRARIAN"); var receipt = new FeeReceiptResponse(9L, "GD-9", "BL-9", 7L, "Bạn đọc Bình", "THE-007", 12L,
                "Thủ thư An", OffsetDateTime.parse("2026-10-12T20:00:00+07:00"), 30000,
                List.of(new FeeReceiptResponse.Item(1L, "RECORDED", "Khoản 1", 30000, 0, 0, 30000)));
        when(service.collect(eq(7L), any(), eq(12L))).thenReturn(receipt);
        mvc.perform(collect(BODY.substring(0, BODY.length() - 1) + ",\"collectedBy\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receiptNumber").value("BL-9"))
                .andExpect(jsonPath("$.collectedBy").value(12)).andExpect(jsonPath("$.items[0].collectedAmountVnd").value(30000));
        verify(service).collect(eq(7L), any(), eq(12L));
        when(service.receipt(9L, 12L)).thenReturn(receipt);
        mvc.perform(get(ROOT + "/9/receipt").header("Authorization", "Bearer test-token")).andExpect(status().isOk());
    }
    @Test void malformedMissingZeroNegativeAndFractionalAmountsFail() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{"{}", BODY.replace("30000", "0"), BODY.replace("30000", "-1"), BODY.replace("30000", "30000.9"),
                BODY.replace("30000", "null"), BODY.replace("e4c9cc1f-490c-4d15-9280-e45b7e50ab7e", "not-a-uuid"),
                BODY.replace("a".repeat(64), "bad")}) mvc.perform(collect(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void stalePreviewAndDatabaseFailureReuseExistingErrorShape() throws Exception {
        token("LIBRARIAN");
        when(service.collect(eq(7L), any(), eq(12L))).thenThrow(new ApiException(HttpStatus.CONFLICT, "FEE_DEBT_CHANGED", "Khoản nợ đã thay đổi."));
        mvc.perform(collect(BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEE_DEBT_CHANGED"));
        when(service.collect(eq(7L), any(), eq(12L))).thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FEE_PAYMENT_FAILED", "Giao dịch đã hủy."));
        mvc.perform(collect(BODY)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Giao dịch đã hủy."));
    }
}
