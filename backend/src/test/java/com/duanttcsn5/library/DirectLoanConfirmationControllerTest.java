package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.LoanService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectLoanConfirmationControllerTest {
    private static final String URL = "/api/v1/loans/direct";
    private static final UUID KEY = UUID.fromString("733b7ee6-79b0-4a77-96d7-74a8ca12a0bb");
    private static final String BODY = """
            {"requestId":"733b7ee6-79b0-4a77-96d7-74a8ca12a0bb","cardNumber":"TV-12","barcodes":["BC-1","BC-2"]}
            """;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LoanService loans() { return mock(LoanService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private LoanService service;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh(); service = context.getBean(LoanService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    private void token(String code) {
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "HS256").subject("12").claim("tokenVersion", 0).build());
        var user = new User(); user.setId(12L); user.setStatus("ACTIVE");
        var role = new Role(); role.setCode(code); user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }
    @Test void anonymousInvalidJwtAndReaderAreRejectedBeforeService() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        token("READER"); mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffConfirmAllBarcodesAndCreatorComesFromJwt() throws Exception {
        var at = OffsetDateTime.parse("2026-10-08T09:00:00+07:00");
        var loan = new LoanDetailResponse(80L, "PM-TEST", null, 99L, "Bạn đọc", 12L, "Thủ thư", at, List.of(
                new LoanDetailResponse.Item(1L, 1L, "BC-1", 50L, "Sách Java", at, at.plusDays(14)),
                new LoanDetailResponse.Item(2L, 2L, "BC-2", 51L, "Sách React", at, at.plusDays(14))));
        when(service.createDirectLoan(new CreateDirectLoanRequest(KEY, "TV-12", List.of("BC-1", "BC-2")), 12L))
                .thenReturn(new DirectLoanResponse(loan, null, "Đã ghi toàn bộ lượt mượn thành công."));
        for (String code : List.of("LIBRARIAN", "LIBRARY_MANAGER", "ADMIN")) {
            token(code); mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                    .header("Authorization", "Bearer test-token")).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.loan.id").value(80)).andExpect(jsonPath("$.loan.items.length()").value(2))
                    .andExpect(jsonPath("$.loan.createdById").value(12));
        }
    }
    @Test void invalidBodiesNeverReachService() throws Exception {
        token("LIBRARIAN");
        for (String body : List.of("{}", BODY.replace(KEY.toString(), "bad"), BODY.replace("TV-12", " "),
                BODY.replace("\"BC-1\",\"BC-2\"", ""), BODY.replace("BC-1", "x".repeat(101)),
                BODY.replace("BC-2", " "), BODY.replace("[\"BC-1\",\"BC-2\"]", "null"))) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());
        }
        verifyNoInteractions(service);
    }
    @Test void unexpectedFailureUsesSafeExistingErrorContract() throws Exception {
        token("LIBRARIAN"); when(service.createDirectLoan(any(), eq(12L))).thenThrow(new IllegalStateException("SQL secret"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Hệ thống đang gặp lỗi. Vui lòng thử lại sau."));
    }

    @Test void rolledBackWriteFailureExplainsThatNoPartialLoanWasSaved() throws Exception {
        token("LIBRARIAN");
        String message = "Không thể ghi trọn vẹn lượt mượn. Toàn bộ thay đổi của lượt đã được hủy. Vui lòng thử lại.";
        when(service.createDirectLoan(any(), eq(12L))).thenThrow(new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR, "DIRECT_LOAN_SAVE_FAILED", message));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DIRECT_LOAN_SAVE_FAILED"))
                .andExpect(jsonPath("$.message").value(message));
    }
}
