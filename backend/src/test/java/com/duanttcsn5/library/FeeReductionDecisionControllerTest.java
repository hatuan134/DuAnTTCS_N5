package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.FeeReductionController;
import com.duanttcsn5.library.dto.loan.FeeReductionRequestResponse;
import com.duanttcsn5.library.dto.loan.RejectFeeReductionRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeeReductionDecisionControllerTest {
    static final String ROOT = "/api/v1/overdue-fees/reduction-requests/2";
    @Configuration @EnableWebMvc @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({SecurityConfig.class, FeeReductionController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean FeeReductionService service() { return mock(FeeReductionService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    }
    AnnotationConfigWebApplicationContext context;
    FeeReductionService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh(); service = context.getBean(FeeReductionService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    User token(String roleCode) {
        doReturn(Jwt.withTokenValue("test-token").header("alg", "HS256").subject("12")
                .claim("tokenVersion", 0).build()).when(context.getBean(JwtService.class)).decode("test-token");
        User user = new User(); user.setId(12L); user.setStatus("ACTIVE"); user.setFullName("Quản lý Hạnh");
        Role role = new Role(); role.setCode(roleCode); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user)); return user;
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder call(String action, String body) {
        return post(ROOT + "/" + action).header("Authorization", "Bearer test-token").contentType("application/json").content(body);
    }
    FeeReductionRequestResponse saved() {
        var at = OffsetDateTime.parse("2026-10-12T10:00:00+07:00");
        return new FeeReductionRequestResponse(2L, 5L, 8L, "PM-008", "Bạn đọc Bình", "Mắt biếc", "LIB-001",
                10L, "Thủ thư An", 15000, 10000, 5000, "Hoàn cảnh khó khăn", at, "APPROVED",
                0, 10000, 12L, "Quản lý Hạnh", at, null);
    }
    @Test void anonymousInvalidJwtInactiveAndRevokedTokenCannotDecide() throws Exception {
        for (String action : new String[]{"approve", "reject"}) {
            mvc.perform(post(ROOT + "/" + action)).andExpect(status().isUnauthorized());
            doThrow(new JwtException("bad")).when(context.getBean(JwtService.class)).decode("test-token");
            mvc.perform(call(action, "{\"reason\":\"Lý do\"}")).andExpect(status().isUnauthorized());
            var user = token("LIBRARY_MANAGER"); user.setStatus("DISABLED");
            mvc.perform(call(action, "{\"reason\":\"Lý do\"}")).andExpect(status().isUnauthorized());
            user = token("LIBRARY_MANAGER"); user.setTokenVersion(1);
            mvc.perform(call(action, "{\"reason\":\"Lý do\"}")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void librarianAdminAndReaderCannotApproveOrRejectDirectly() throws Exception {
        for (String role : new String[]{"LIBRARIAN", "READER"}) {
            token(role);
            mvc.perform(call("approve", "{}")).andExpect(status().isForbidden());
            mvc.perform(call("reject", "{\"reason\":\"Lý do\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void managerDecisionUsesJwtActorAndReturnsWaiverAndRemainingAmount() throws Exception {
        token("LIBRARY_MANAGER"); when(service.approve(2L, 12L)).thenReturn(saved());
        mvc.perform(call("approve", "{\"decidedBy\":999,\"approvedAmountVnd\":15000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedAmountVnd").value(10000))
                .andExpect(jsonPath("$.outstandingAmountVnd").value(5000)).andExpect(jsonPath("$.decidedBy").value(12));
        verify(service).approve(2L, 12L);
        when(service.reject(eq(2L), any(), eq(12L))).thenReturn(saved());
        mvc.perform(call("reject", "{\"reason\":\"Chưa đủ căn cứ\"}")).andExpect(status().isOk());
        verify(service).reject(2L, new RejectFeeReductionRequest("Chưa đủ căn cứ"), 12L);
    }
    @Test void missingBlankAndLongRejectionReasonsFailValidation() throws Exception {
        token("LIBRARY_MANAGER");
        for (String body : new String[]{"{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(1001) + "\"}"}) {
            mvc.perform(call("reject", body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
    @Test void repeatedAndPaidConflictsKeepExistingVietnameseErrorContract() throws Exception {
        token("LIBRARY_MANAGER");
        for (String code : new String[]{"FEE_REDUCTION_PROCESSED", "FEE_REDUCTION_PAID"}) {
            doThrow(new ApiException(HttpStatus.CONFLICT, code, "Không thể phê duyệt.")).when(service).approve(2L, 12L);
            mvc.perform(call("approve", "{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
        }
    }
}
