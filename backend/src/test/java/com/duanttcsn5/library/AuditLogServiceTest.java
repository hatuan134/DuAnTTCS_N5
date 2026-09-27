package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.service.AuditLogService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    AuditLogRepository auditLogRepository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("Lọc nhật ký đăng nhập theo khoảng ngày và người thực hiện")
    void search_LoginGroup_MapsResult() {
        var row = new AuditLogRepository.AuditLogRow(
                10L,
                1L,
                "Quản trị hệ thống",
                "admin@libra.edu.vn",
                "Quản trị hệ thống",
                "LOGIN_SUCCESS",
                "USER",
                "1",
                null,
                "{\"result\":\"SUCCESS\"}",
                "127.0.0.1",
                OffsetDateTime.of(2026, 9, 27, 8, 0, 0, 0, ZoneOffset.ofHours(7)),
                "Quản trị hệ thống",
                "admin@libra.edu.vn",
                null
        );

        when(auditLogRepository.findByFilters(any(), any(), eq(1L), anyList(), eq("admin")))
                .thenReturn(List.of(row));

        var result = service.search(
                LocalDate.of(2026, 9, 27),
                LocalDate.of(2026, 9, 27),
                1L,
                "LOGIN",
                "admin");

        assertEquals(1, result.size());
        assertEquals("LOGIN", result.get(0).actionGroup());
        assertEquals("Đăng nhập thành công", result.get(0).actionLabel());
        assertTrue(result.get(0).actor().contains("admin@libra.edu.vn"));
    }

    @Test
    @DisplayName("Đăng nhập thất bại với email không tồn tại vẫn được ghi nhật ký")
    void logUnknownLoginFailure_InsertsAuditRow() {
        service.logLoginFailedUnknownEmail("missing@libra.edu.vn", "10.0.0.10");

        verify(auditLogRepository).insert(
                isNull(),
                eq("LOGIN_FAILED"),
                eq("LOGIN_ATTEMPT"),
                eq("missing@libra.edu.vn"),
                any(String.class),
                eq("10.0.0.10"));
    }

    @Test
    @DisplayName("Có điểm tích hợp ghi nhật ký khi cấp thẻ thư viện")
    void logLibraryCardIssued_InsertsExpectedAction() {
        service.logLibraryCardIssued(
                5L,
                100L,
                "TV20260001",
                20L,
                "Nguyễn Văn An",
                "Thẻ sinh viên",
                "2027-09-27",
                "192.168.1.20");

        verify(auditLogRepository).insert(
                eq(5L),
                eq("LIBRARY_CARD_ISSUED"),
                eq("LIBRARY_CARD"),
                eq("100"),
                any(String.class),
                eq("192.168.1.20"));
    }
}
