package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.LoanController;
import com.duanttcsn5.library.dto.loan.ReservationLoanResponse;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanContextResponse;
import com.duanttcsn5.library.dto.loan.LoanDatePreviewResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.ReservationPickupExpiredException;
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

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationLoanControllerTest {
    private static final String URL = "/api/v1/reservations/21/loan";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, LoanController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean LoanService reservations() { return mock(LoanService.class); }
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(String json) {
        return post(URL).header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void missingAndInvalidJwtCannotConvertOrReadCard() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"cardNumber\":\"TV-0012\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reservations/21/loan-context")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reservations/21/pickup-check")).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(post(URL).header("Authorization", "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardNumber\":\"TV-0012\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void readerCannotConvertOrReadAnotherReadersCard() throws Exception {
        token("READER");
        mvc.perform(post("/api/v1/reservations/21/pickup-check").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void existingStaffRolesCanCreateAndReadContext() throws Exception {
        var result = new ReservationLoanResponse(81L, "PM-DEMO", 21L, 99L, "Nguyễn Văn An", "TV-0012",
                7L, "Mắt biếc", 31L, "LIB-031", OffsetDateTime.now(), "Đã lập phiếu mượn thành công.");
        when(service.createFromReservation(21L, 12L, "TV-0012", null, null, null)).thenReturn(result);
        when(service.pickupContext(21L)).thenReturn(new ReservationLoanContextResponse("TV-0012", false, null));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(81))
                    .andExpect(jsonPath("$.readerId").value(99)).andExpect(jsonPath("$.barcode").value("LIB-031"))
                    .andExpect(jsonPath("$.loanNumber").value("PM-DEMO"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
            mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.cardNumber").value("TV-0012"));
        }
    }

    @Test
    void missingNullBlankAndOversizeCardAreValidatedBeforeService() throws Exception {
        token("LIBRARIAN");
        for (String body : new String[]{"{}", "{\"cardNumber\":null}", "{\"cardNumber\":\"\"}",
                "{\"cardNumber\":\"   \"}", "{\"cardNumber\":\"" + "x".repeat(101) + "\"}"}) {
            mvc.perform(submit(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void mismatchAndRepeatedConversionUseVietnameseDomainErrors() throws Exception {
        token("LIBRARIAN");
        when(service.createFromReservation(21L, 12L, "WRONG", null, null, null)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "RESERVATION_CARD_MISMATCH", "Mã thẻ không đúng với bạn đọc sở hữu đơn đặt giữ."));
        mvc.perform(submit("{\"cardNumber\":\"WRONG\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_CARD_MISMATCH"));
        when(service.createFromReservation(21L, 12L, "TV-0012", null, null, null)).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_ALREADY_CONVERTED", "Đơn đã chuyển thành phiếu mượn."));
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_CONVERTED"));
    }

    @Test
    void previewAndConfirmationExposeTheComputedDatesAndPassExpectedValues() throws Exception {
        token("LIBRARIAN");
        LocalDate borrow = LocalDate.of(2026, 10, 7), due = LocalDate.of(2026, 10, 22);
        OffsetDateTime dueAt = OffsetDateTime.parse("2026-10-22T17:00:00+07:00");
        var dates = new LoanDatePreviewResponse(borrow, "Thẻ sinh viên", 14,
                LocalDate.of(2026, 10, 21), due, dueAt, true, List.of(LocalDate.of(2026, 10, 21)));
        when(service.pickupContext(21L)).thenReturn(new ReservationLoanContextResponse("TV-0012", false, null, dates, null));
        when(service.createFromReservation(21L, 12L, "TV-0012", borrow, dueAt, 14)).thenReturn(
                new ReservationLoanResponse(81L, "PM-DEMO", 21L, 99L, "Nguyễn Văn An", "TV-0012",
                        7L, "Mắt biếc", 31L, "LIB-031", OffsetDateTime.now(), "Đã lập phiếu mượn.", dates));
        mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dates.borrowDate").value("2026-10-07"))
                .andExpect(jsonPath("$.dates.loanDays").value(14))
                .andExpect(jsonPath("$.dates.dueDate").value("2026-10-22"))
                .andExpect(jsonPath("$.dates.adjusted").value(true));
        mvc.perform(submit("""
                {"cardNumber":"TV-0012","expectedBorrowDate":"2026-10-07",
                 "expectedDueAt":"2026-10-22T17:00:00+07:00","expectedLoanDays":14}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dates.dueDate").value("2026-10-22"));
        verify(service).createFromReservation(21L, 12L, "TV-0012", borrow, dueAt, 14);
    }

    @Test
    void missingCalendarIsShownInContextAndInvalidExpectedDaysAreRejected() throws Exception {
        token("LIBRARIAN");
        when(service.pickupContext(21L)).thenReturn(new ReservationLoanContextResponse("TV-0012", false, null,
                null, "Lịch làm việc chưa được cấu hình đủ 7 ngày."));
        mvc.perform(get("/api/v1/reservations/21/loan-context").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateError").value("Lịch làm việc chưa được cấu hình đủ 7 ngày."));
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\",\"expectedLoanDays\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(service, never()).createFromReservation(any(), any(), any(), any(), any(), any());
    }

    @Test void expiredConversionReturnsConflictWithReReservationInstruction() throws Exception {
        token("LIBRARIAN");
        when(service.createFromReservation(21L, 12L, "TV-0012", null, null, null))
                .thenThrow(new ReservationPickupExpiredException(21L));
        mvc.perform(submit("{\"cardNumber\":\"TV-0012\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_PICKUP_EXPIRED"))
                .andExpect(jsonPath("$.message").value(ReservationPickupExpiredException.MESSAGE))
                .andExpect(jsonPath("$.details.status").value("EXPIRED"))
                .andExpect(jsonPath("$.details.reservationId").value(21));
    }
    @Test void allExistingStaffRolesCanCheckExpiryAndReceiveSafeSummary() throws Exception {
        var checked = OffsetDateTime.parse("2026-10-07T17:01:00+07:00");
        var deadline = checked.minusMinutes(1);
        var summary = new com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse(
                21L, 7L, "Mắt biếc", 31L, "LIB-031", 99L, "Nguyễn Văn An", "EXPIRED", checked.minusDays(3), deadline);
        var result = new ReservationLoanContextResponse("TV-0012", false, null, null, null,
                "EXPIRED", true, deadline, checked, ReservationPickupExpiredException.MESSAGE, "AVAILABLE", summary);
        when(service.checkPickup(21L, 12L)).thenReturn(result);
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(post("/api/v1/reservations/21/pickup-check").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.expired").value(true))
                    .andExpect(jsonPath("$.status").value("EXPIRED"))
                    .andExpect(jsonPath("$.copyStatus").value("AVAILABLE"))
                    .andExpect(jsonPath("$.reservation.id").value(21))
                    .andExpect(jsonPath("$.reservation.readerId").value(99))
                    .andExpect(jsonPath("$.reservation.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.reservation.email").doesNotExist());
        }
        verify(service, times(3)).checkPickup(21L, 12L);
        verify(service, never()).createFromReservation(any(), any(), any(), any(), any(), any());
    }

    @Test void staffReadApisExposeSavedLoanDetailsAndOriginalCreatorOnly() throws Exception {
        var borrowed = OffsetDateTime.parse("2026-10-07T17:00:00+07:00");
        var due = borrowed.plusDays(14);
        var item = new LoanDetailResponse.Item(91L, 31L, "LIB-031", 7L, "Mắt biếc", borrowed, due);
        when(service.loanDetail(81L, 12L)).thenReturn(new LoanDetailResponse(81L, "PM-SAVED", 21L,
                99L, "Nguyễn Văn An", 3L, "Thủ thư lập phiếu", borrowed, List.of(item)));
        when(service.listLoans(12L)).thenReturn(List.of(new LoanSummaryResponse(81L, "PM-SAVED", 99L,
                "Nguyễn Văn An", 3L, "Thủ thư lập phiếu", borrowed, 1)));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get("/api/v1/loans/81").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.loanNumber").value("PM-SAVED"))
                    .andExpect(jsonPath("$.createdById").value(3))
                    .andExpect(jsonPath("$.createdByName").value("Thủ thư lập phiếu"))
                    .andExpect(jsonPath("$.borrowedAt").exists())
                    .andExpect(jsonPath("$.items[0].barcode").value("LIB-031"))
                    .andExpect(jsonPath("$.items[0].bookTitle").value("Mắt biếc"))
                    .andExpect(jsonPath("$.items[0].dueAt").exists())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
            mvc.perform(get("/api/v1/loans").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(81))
                    .andExpect(jsonPath("$[0].itemCount").value(1));
        }
        verify(service, never()).createFromReservation(any(), any(), any(), any(), any(), any());
    }
    @Test void loanListAndDetailRequireValidJwtAndStaffRole() throws Exception {
        for (String url : new String[]{"/api/v1/loans", "/api/v1/loans/81"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(get("/api/v1/loans/81").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        token("READER");
        for (String url : new String[]{"/api/v1/loans", "/api/v1/loans/81"}) {
            mvc.perform(get(url).header("Authorization", "Bearer test-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void invalidAndMissingLoanIdsUseExistingErrorResponse() throws Exception {
        token("LIBRARIAN");
        mvc.perform(get("/api/v1/loans/abc").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        when(service.loanDetail(0L, 12L)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_LOAN_ID", "Mã phiếu mượn không hợp lệ."));
        mvc.perform(get("/api/v1/loans/0").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LOAN_ID"));
        when(service.loanDetail(999L, 12L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "LOAN_NOT_FOUND", "Không tìm thấy phiếu mượn."));
        mvc.perform(get("/api/v1/loans/999").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
    }
}
