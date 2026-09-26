package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.InitialPasswordController;
import com.duanttcsn5.library.controller.UserManagementController;
import com.duanttcsn5.library.dto.account.AccountResponse;
import com.duanttcsn5.library.dto.account.CreateAccountRequest;
import com.duanttcsn5.library.dto.account.SetInitialPasswordRequest;
import com.duanttcsn5.library.dto.account.ValidateInitialPasswordTokenResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.InitialPasswordService;
import com.duanttcsn5.library.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    private MockMvc adminMockMvc;
    private MockMvc publicMockMvc;

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private InitialPasswordService initialPasswordService;

    @InjectMocks
    private UserManagementController userManagementController;

    @InjectMocks
    private InitialPasswordController initialPasswordController;

    @BeforeEach
    void setUp() {
        adminMockMvc = MockMvcBuilders.standaloneSetup(userManagementController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        publicMockMvc = MockMvcBuilders.standaloneSetup(initialPasswordController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Admin tạo tài khoản hợp lệ trả về HTTP 201 Created")
    void testCreateAccount_Success() throws Exception {
        AccountResponse response = new AccountResponse(
                10L,
                "Trần Văn B",
                "tranvanb@libra.edu.vn",
                "0988776655",
                "LIBRARIAN",
                "Thủ thư",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now());

        when(userManagementService.createAccount(any(CreateAccountRequest.class), any(), anyString()))
                .thenReturn(response);

        String requestJson = """
                {
                    "fullName": "Trần Văn B",
                    "email": "tranvanb@libra.edu.vn",
                    "phone": "0988776655",
                    "role": "LIBRARIAN",
                    "status": "ACTIVE"
                }
                """;

        adminMockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("tranvanb@libra.edu.vn"))
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.roleName").value("Thủ thư"));
    }

    @Test
    @DisplayName("Admin tạo tài khoản khi email đã tồn tại => HTTP 409 Conflict")
    void testCreateAccount_DuplicateEmail_Returns409() throws Exception {
        when(userManagementService.createAccount(any(CreateAccountRequest.class), any(), anyString()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email 'dup@libra.edu.vn' đã tồn tại trong hệ thống."));

        String requestJson = """
                {
                    "fullName": "Trần Văn B",
                    "email": "dup@libra.edu.vn",
                    "phone": "0988776655",
                    "role": "LIBRARIAN",
                    "status": "ACTIVE"
                }
                """;

        adminMockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value(containsString("đã tồn tại")));
    }

    @Test
    @DisplayName("Admin lấy danh sách tài khoản => HTTP 200 OK")
    void testGetAccounts_Success() throws Exception {
        when(userManagementService.getAccounts(any(), any(), any()))
                .thenReturn(List.of(new AccountResponse(
                        1L, "Admin User", "admin@libra.edu.vn", "0900000000", "ADMIN", "Quản trị hệ thống", "ACTIVE",
                        OffsetDateTime.now(), OffsetDateTime.now())));

        adminMockMvc.perform(get("/api/v1/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@libra.edu.vn"));
    }

    @Test
    @DisplayName("Validate token mật khẩu lần đầu hợp lệ => HTTP 200 OK")
    void testValidateInitialPasswordToken_Valid() throws Exception {
        when(initialPasswordService.validateToken("valid-token"))
                .thenReturn(new ValidateInitialPasswordTokenResponse(true, "user@libra.edu.vn", "Nguyễn Văn A"));

        publicMockMvc.perform(get("/api/v1/auth/initial-password")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value("user@libra.edu.vn"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
    }

    @Test
    @DisplayName("Đặt mật khẩu lần đầu thành công => HTTP 200 OK")
    void testSetInitialPassword_Success() throws Exception {
        String requestJson = """
                {
                    "token": "valid-token",
                    "password": "Password@123"
                }
                """;

        publicMockMvc.perform(post("/api/v1/auth/initial-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("thành công")));
    }
}
