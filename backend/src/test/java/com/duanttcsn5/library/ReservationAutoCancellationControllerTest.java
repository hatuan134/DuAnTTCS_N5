package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.dto.book.AutoCancellationRunResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtAuthenticationFilter;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.security.RestAccessDeniedHandler;
import com.duanttcsn5.library.security.RestAuthenticationEntryPoint;
import com.duanttcsn5.library.service.BookReservationService;
import com.duanttcsn5.library.service.ReservationAutoCancellationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationAutoCancellationControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({
            SecurityConfig.class,
            BookReservationController.class,
            GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class
    })
    static class Config {
        @Bean
        BookReservationService reservations() {
            return mock(BookReservationService.class);
        }

        @Bean
        ReservationAutoCancellationService autoCancellationService() {
            return mock(ReservationAutoCancellationService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private ReservationAutoCancellationService autoCancellationService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        autoCancellationService = context.getBean(ReservationAutoCancellationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private void token(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);
        User user = new User();
        user.setId(12L);
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode(roleCode);
        user.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Quản lý thư viện gọi endpoint huỷ tự động thành công")
    void manager_canTriggerAutoCancel() throws Exception {
        token("LIBRARY_MANAGER");
        OffsetDateTime now = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        AutoCancelledReservationResponse item = new AutoCancelledReservationResponse(
                1L, 10L, "Lập trình Java", 100L, "Nguyễn Văn An", 20L, "BC-001",
                "CANCELLED", now.minusDays(4), now.minusHours(7), now, "Hệ thống", "Đã huỷ do quá hạn nhận"
        );
        when(autoCancellationService.processOverdueReservations()).thenReturn(List.of(item));

        mvc.perform(post("/api/v1/reservations/auto-cancel-overdue")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].cancellationReason").value("Đã huỷ do quá hạn nhận"))
                .andExpect(jsonPath("$[0].cancelledByName").value("Hệ thống"));

        verify(autoCancellationService).processOverdueReservations();
    }

    @Test
    @DisplayName("Admin gọi endpoint huỷ tự động thành công")
    void admin_canTriggerAutoCancel() throws Exception {
        token("ADMIN");
        when(autoCancellationService.processOverdueReservations()).thenReturn(List.of());

        mvc.perform(post("/api/v1/reservations/auto-cancel-overdue")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(autoCancellationService).processOverdueReservations();
    }

    @Test
    @DisplayName("Quản lý thư viện gọi endpoint kèm tham số checkTime thành công")
    void manager_canTriggerWithCheckTime() throws Exception {
        token("LIBRARY_MANAGER");
        OffsetDateTime checkTime = OffsetDateTime.parse("2026-10-09T00:30:00+07:00");
        when(autoCancellationService.processOverdueReservationsAt(any())).thenReturn(List.of());

        mvc.perform(post("/api/v1/reservations/auto-cancel-overdue")
                        .param("checkTime", checkTime.toString())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(autoCancellationService).processOverdueReservationsAt(any());
    }

    @Test
    @DisplayName("Bạn đọc không có quyền kích hoạt huỷ tự động -> 403 Forbidden")
    void reader_cannotTriggerAutoCancel() throws Exception {
        token("READER");
        mvc.perform(post("/api/v1/reservations/auto-cancel-overdue")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chưa đăng nhập không được kích hoạt huỷ tự động -> 401 Unauthorized")
    void anonymous_cannotTriggerAutoCancel() throws Exception {
        mvc.perform(post("/api/v1/reservations/auto-cancel-overdue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Quản lý thư viện xem kết quả lần chạy gần nhất thành công")
    void manager_canGetLatestRun() throws Exception {
        token("LIBRARY_MANAGER");
        AutoCancellationRunResponse latestRun = new AutoCancellationRunResponse(
                1L, LocalDate.of(2026, 10, 9),
                OffsetDateTime.parse("2026-10-09T00:30:00+07:00"),
                OffsetDateTime.parse("2026-10-09T00:30:01+07:00"),
                "SUCCESS", 5, 5, 3, 2, 0, null, "SYSTEM", List.of()
        );
        when(autoCancellationService.getLatestRun()).thenReturn(Optional.of(latestRun));

        mvc.perform(get("/api/v1/reservations/auto-cancel-runs/latest")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalIdentified").value(5))
                .andExpect(jsonPath("$.totalCancelled").value(5))
                .andExpect(jsonPath("$.totalTransferred").value(3))
                .andExpect(jsonPath("$.totalReleased").value(2));

        verify(autoCancellationService).getLatestRun();
    }

    @Test
    @DisplayName("Quản lý thư viện xem danh sách lịch sử các lần chạy")
    void manager_canGetAllRuns() throws Exception {
        token("LIBRARY_MANAGER");
        when(autoCancellationService.getAllRuns()).thenReturn(List.of());

        mvc.perform(get("/api/v1/reservations/auto-cancel-runs")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(autoCancellationService).getAllRuns();
    }

    @Test
    @DisplayName("Bạn đọc không có quyền xem kết quả lần chạy -> 403 Forbidden")
    void reader_cannotGetLatestRun() throws Exception {
        token("READER");
        mvc.perform(get("/api/v1/reservations/auto-cancel-runs/latest")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Quản lý thư viện xem danh sách đơn bị huỷ tự động trong 30 ngày gần nhất thành công")
    void manager_canGetAutoCancelledLast30Days() throws Exception {
        token("LIBRARY_MANAGER");
        when(autoCancellationService.getAutoCancelledReservationsLast30Days()).thenReturn(List.of());

        mvc.perform(get("/api/v1/reservations/auto-cancelled-last-30-days")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(autoCancellationService).getAutoCancelledReservationsLast30Days();
    }

    @Test
    @DisplayName("Bạn đọc không có quyền tra cứu đơn huỷ tự động 30 ngày -> 403 Forbidden")
    void reader_cannotGetAutoCancelledLast30Days() throws Exception {
        token("READER");
        mvc.perform(get("/api/v1/reservations/auto-cancelled-last-30-days")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chưa đăng nhập không được tra cứu đơn huỷ tự động -> 401 Unauthorized")
    void anonymous_cannotGetAutoCancelledLast30Days() throws Exception {
        mvc.perform(get("/api/v1/reservations/auto-cancelled-last-30-days"))
                .andExpect(status().isUnauthorized());
    }
}
