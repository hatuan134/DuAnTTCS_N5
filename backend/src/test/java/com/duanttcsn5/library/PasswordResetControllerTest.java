package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.PasswordResetController;
import com.duanttcsn5.library.dto.auth.ForgotPasswordRequest;
import com.duanttcsn5.library.dto.auth.ForgotPasswordResponse;
import com.duanttcsn5.library.dto.auth.ResetPasswordRequest;
import com.duanttcsn5.library.dto.auth.ResetPasswordResponse;
import com.duanttcsn5.library.dto.auth.ValidateResetPasswordTokenResponse;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController passwordResetController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password: Thành công trả về thông báo trung tính")
    void testForgotPassword_Success() throws Exception {
        when(passwordResetService.requestPasswordReset(any(ForgotPasswordRequest.class), anyString()))
                .thenReturn(new ForgotPasswordResponse("Nếu email của bạn tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đến hòm thư."));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@libra.edu.vn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("hướng dẫn đặt lại mật khẩu")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password: Email không hợp lệ trả về lỗi VALIDATION_ERROR")
    void testForgotPassword_InvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/reset-password/validate: Token hợp lệ trả về 200 OK")
    void testValidateToken_Success() throws Exception {
        when(passwordResetService.validateToken("test-token"))
                .thenReturn(new ValidateResetPasswordTokenResponse(true, "r***r@libra.edu.vn", "Nguyen Van A"));

        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password: Thành công trả về 200 OK")
    void testResetPassword_Success() throws Exception {
        when(passwordResetService.resetPassword(any(ResetPasswordRequest.class), anyString()))
                .thenReturn(new ResetPasswordResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới."));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\",\"newPassword\":\"Password123\",\"confirmPassword\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("thành công")));
    }
}
