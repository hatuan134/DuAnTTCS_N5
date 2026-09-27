package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.AuditLogController;
import com.duanttcsn5.library.dto.audit.AuditFilterOptionsResponse;
import com.duanttcsn5.library.dto.audit.AuditLogResponse;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    AuditLogController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Tra cứu nhật ký trả về đầy đủ trường bắt buộc")
    void search_ReturnsAuditFields() throws Exception {
        when(auditLogService.search(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new AuditLogResponse(
                        1L,
                        OffsetDateTime.now(),
                        1L,
                        "Admin (admin@libra.edu.vn)",
                        "Quản trị hệ thống",
                        "LOGIN_SUCCESS",
                        "LOGIN",
                        "Đăng nhập thành công",
                        "Admin (admin@libra.edu.vn)",
                        "Tài khoản người dùng",
                        "USER",
                        "1",
                        "127.0.0.1",
                        "Đăng nhập thành công vào hệ thống.")));

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[0].actor").exists())
                .andExpect(jsonPath("$[0].action").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$[0].target").exists())
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"));
    }

    @Test
    @DisplayName("API từ chối sửa nhật ký")
    void patch_IsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/audit-logs/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_IMMUTABLE"));
    }

    @Test
    @DisplayName("API từ chối xóa nhật ký")
    void delete_IsRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/audit-logs/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_IMMUTABLE"));
    }
}
