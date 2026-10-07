package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanItemResponse;
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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import org.springframework.http.MediaType;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectLoanItemsControllerTest {
    private static final String URL = "/api/v1/loans/direct/items/preview";
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
        context.register(Config.class); context.refresh();
        service = context.getBean(LoanService.class);
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


    private static final String BODY = """
            {"cardNumber":"TV-0012","barcode":"BC-1","selectedBarcodes":[]}
            """;

    @Test void anonymousInvalidJwtAndReaderCannotPreviewItems() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad")).thenThrow(new JwtException("invalid"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        token("READER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffCanPreviewMinimalCopyDetails() throws Exception {
        var request = new AddDirectLoanItemRequest("TV-0012", "BC-1", List.of());
        when(service.previewDirectLoanItem(request, 12L))
                .thenReturn(new DirectLoanItemResponse(1L, 50L, "BC-1", "Lập trình Java", 3L));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.barcode").value("BC-1"))
                    .andExpect(jsonPath("$.bookTitle").value("Lập trình Java"))
                    .andExpect(jsonPath("$.remainingBooks").value(3))
                    .andExpect(jsonPath("$.readerName").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
    }
    @Test void invalidBodyIsRejectedBeforeService() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{
                "{}", BODY.replace("BC-1", " "), BODY.replace("[]", "null"),
                BODY.replace("[]", "[\"\"]"), BODY.replace("BC-1", "x".repeat(101)),
                BODY.replace("TV-0012", " ")}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        }
        verifyNoInteractions(service);
    }
    @Test void missingAndUnavailableBarcodesUseExistingErrorContract() throws Exception {
        token("LIBRARIAN");
        when(service.previewDirectLoanItem(any(), eq(12L))).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "LOAN_DRAFT_COPY_NOT_FOUND", "Không tìm thấy sách theo mã vạch đã nhập."));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_DRAFT_COPY_NOT_FOUND"));
        for (String label : new String[]{"Đang mượn", "Đang sửa chữa"}) {
            String message = "Bản sao không ở trạng thái Sẵn sàng. Trạng thái hiện tại: " + label + ".";
            when(service.previewDirectLoanItem(any(), eq(12L))).thenThrow(new ApiException(HttpStatus.CONFLICT,
                    "LOAN_DRAFT_COPY_NOT_AVAILABLE", message));
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LOAN_DRAFT_COPY_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.message").value(message));
        }
    }

    @Test void quotaErrorUsesExistingVietnameseErrorContract() throws Exception {
        token("LIBRARIAN");
        when(service.previewDirectLoanItem(any(), eq(12L))).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "LOAN_DRAFT_LIMIT_EXCEEDED", "Không thể thêm sách: lượt mượn sẽ vượt giới hạn."));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_DRAFT_LIMIT_EXCEEDED"));
    }
}
