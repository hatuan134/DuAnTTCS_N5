package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.ReaderController;
import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.ReaderRegistrationService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReaderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReaderRegistrationService readerRegistrationService;

    @InjectMocks
    private ReaderController readerController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(readerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("S1-03 HTTP: Đăng ký với Email đã có trong hệ thống - Trả về 409 Conflict và gợi ý Quên mật khẩu")
    void testRegister_DuplicateEmail_Returns409() throws Exception {
        when(readerRegistrationService.registerReader(any(ReaderRegistrationRequest.class), anyString()))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_EMAIL",
                        "Email 'sv01@ictu.edu.vn' đã được đăng ký trong hệ thống. Nếu bạn đã có tài khoản, vui lòng sử dụng chức năng Quên mật khẩu (chuyển hướng: /forgot-password?email=sv01%40ictu.edu.vn)."
                ));

        String requestJson = """
                {
                    "fullName": "Nguyen Van A",
                    "email": "sv01@ictu.edu.vn",
                    "memberCode": "B21DCCN001",
                    "dateOfBirth": "2003-05-15",
                    "phone": "0987654321",
                    "address": "Ha Noi",
                    "password": "Password123"
                }
                """;

        mockMvc.perform(post("/api/v1/readers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value(containsString("Quên mật khẩu")));
    }

    @Test
    @DisplayName("S1-03 HTTP: Đăng ký với Mã sinh viên/cán bộ đã có - Trả về 409 Conflict và gợi ý Quên mật khẩu")
    void testRegister_DuplicateMemberCode_Returns409() throws Exception {
        when(readerRegistrationService.registerReader(any(ReaderRegistrationRequest.class), anyString()))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_MEMBER_CODE",
                        "Mã sinh viên/cán bộ 'B21DCCN999' đã được đăng ký hồ sơ bạn đọc. Vui lòng sử dụng chức năng Quên mật khẩu hoặc liên hệ thủ thư thư viện."
                ));

        String requestJson = """
                {
                    "fullName": "Tran Thi B",
                    "email": "new.email@ictu.edu.vn",
                    "memberCode": "B21DCCN999",
                    "dateOfBirth": "2002-08-20",
                    "phone": "0912345678",
                    "address": "Thai Nguyen",
                    "password": "Password123"
                }
                """;

        mockMvc.perform(post("/api/v1/readers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MEMBER_CODE"))
                .andExpect(jsonPath("$.message").value(containsString("Quên mật khẩu")));
    }

    @Test
    @DisplayName("S1-03 HTTP: Đăng ký với Email và Mã sinh viên mới - Trả về 201 Created thành công")
    void testRegister_Success_Returns201() throws Exception {
        ReaderRegistrationResponse mockResponse = new ReaderRegistrationResponse(
                15L,
                "Le Van C",
                "levanc@ictu.edu.vn",
                "B21DCCN123",
                "PENDING",
                OffsetDateTime.now(),
                "Đăng ký tài khoản bạn đọc thành công! Hồ sơ đang ở trạng thái chờ duyệt."
        );

        when(readerRegistrationService.registerReader(any(ReaderRegistrationRequest.class), anyString()))
                .thenReturn(mockResponse);

        String requestJson = """
                {
                    "fullName": "Le Van C",
                    "email": "levanc@ictu.edu.vn",
                    "memberCode": "B21DCCN123",
                    "dateOfBirth": "2003-01-10",
                    "phone": "0933333333",
                    "address": "Ha Noi",
                    "password": "SecurePass123"
                }
                """;

        mockMvc.perform(post("/api/v1/readers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(15))
                .andExpect(jsonPath("$.email").value("levanc@ictu.edu.vn"))
                .andExpect(jsonPath("$.memberCode").value("B21DCCN123"))
                .andExpect(jsonPath("$.registrationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("S1-03 HTTP: Kiểm tra API check-duplicate khi Email đã tồn tại")
    void testCheckDuplicate_ReturnsStatus() throws Exception {
        DuplicateCheckResponse response = new DuplicateCheckResponse(
                true,
                false,
                "Email 'test@ictu.edu.vn' đã tồn tại trong hệ thống.",
                null,
                true,
                "/forgot-password?email=test%40ictu.edu.vn"
        );

        when(readerRegistrationService.checkDuplicate("test@ictu.edu.vn", null))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/readers/check-duplicate")
                        .param("email", "test@ictu.edu.vn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailExists").value(true))
                .andExpect(jsonPath("$.memberCodeExists").value(false))
                .andExpect(jsonPath("$.suggestForgotPassword").value(true))
                .andExpect(jsonPath("$.forgotPasswordUrl").value(containsString("/forgot-password")));
    }
}
