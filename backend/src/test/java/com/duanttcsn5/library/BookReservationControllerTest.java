package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.*;
import com.duanttcsn5.library.service.BookReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookReservationControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, BookReservationController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean BookReservationService reservations() { return mock(BookReservationService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    AnnotationConfigWebApplicationContext context;
    BookReservationService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(BookReservationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    private void token(String roleCode) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject("12").claim("tokenVersion", 0).build();
        when(context.getBean(JwtService.class).decode("test-token")).thenReturn(jwt);
        User reader = new User(); reader.setId(12L); reader.setStatus("ACTIVE");
        Role role = new Role(); role.setCode(roleCode); reader.setRole(role);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(reader));
    }

    @Test void guestGets401AndCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/books/7/reservations"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập để đặt giữ đầu sách."));
        verifyNoInteractions(service);
    }

    @Test void invalidTokenGets401() throws Exception {
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void everyStaffRoleGets403() throws Exception {
        for (String role : new String[]{"ADMIN", "LIBRARIAN", "LIBRARY_MANAGER"}) {
            token(role);
            mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void readerGetsCreatedWithPositionAndAuthenticatedIdentity() throws Exception {
        token("READER");
        when(service.reserve(7L, 12L)).thenReturn(new BookReservationResponse(
                100L, 7L, "PENDING", OffsetDateTime.parse("2026-10-03T16:00:00+07:00"),
                2L, "Đặt giữ thành công. Vị trí hiện tại trong hàng đợi: 2.", null, null));
        mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"readerId\":999,\"bookCopyId\":123}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(7)).andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.queuePosition").value(2)).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reservedAt").exists())
                .andExpect(jsonPath("$.bookCopyId").doesNotExist()).andExpect(jsonPath("$.readerId").doesNotExist());
        verify(service).reserve(7L, 12L);
    }

    @Test void cardErrorsUseExistingVietnameseErrorResponse() throws Exception {
        token("READER");
        for (String code : new String[]{"LIBRARY_CARD_EXPIRED", "LIBRARY_CARD_LOCKED"}) {
            doThrow(new ApiException(HttpStatus.FORBIDDEN, code, "Thẻ không đủ điều kiện."))
                    .when(service).reserve(7L, 12L);
            mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.message").value("Thẻ không đủ điều kiện."));
        }
    }

    @Test void readyResponseContainsCopyLocationAndDeadline() throws Exception {
        token("READER");
        var copy = new BookReservationResponse.ReservedCopy(101L, "LIB-101", "KHO-A", "Kho A", "A01", "Kệ Văn học");
        when(service.reserve(7L, 12L)).thenReturn(new BookReservationResponse(100L, 7L, "READY_FOR_PICKUP",
                OffsetDateTime.parse("2026-10-03T16:00:00+07:00"), null, "Đã dành một bản sách.",
                OffsetDateTime.parse("2026-10-07T17:00:00+07:00"), copy));
        mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.reservedCopy.copyId").value(101))
                .andExpect(jsonPath("$.reservedCopy.barcode").value("LIB-101"))
                .andExpect(jsonPath("$.reservedCopy.warehouseName").value("Kho A"))
                .andExpect(jsonPath("$.reservedCopy.shelfCode").value("A01"))
                .andExpect(jsonPath("$.pickupDeadline").exists())
                .andExpect(jsonPath("$.queuePosition").doesNotExist())
                .andExpect(jsonPath("$.readerId").doesNotExist());
    }

    @Test void calendarConflictUsesExistingErrorResponse() throws Exception {
        token("READER");
        doThrow(new ApiException(HttpStatus.CONFLICT, "PICKUP_DEADLINE_NOT_FOUND", "Chưa có ngày mở cửa."))
                .when(service).reserve(7L, 12L);
        mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PICKUP_DEADLINE_NOT_FOUND"));
    }

    @Test void reservationPolicyConflictsReturnSpecificCodeAndVietnameseReason() throws Exception {
        token("READER");
        String[][] errors = {
                {"RESERVATION_LIMIT_REACHED", "Bạn đã đạt giới hạn tối đa 3 đơn đặt giữ đang hiệu lực."},
                {"RESERVATION_ALREADY_ACTIVE", "Bạn đã có đơn đặt giữ đang hiệu lực cho đầu sách “Dế Mèn phiêu lưu ký”."}
        };
        for (String[] error : errors) {
            doThrow(new ApiException(HttpStatus.CONFLICT, error[0], error[1])).when(service).reserve(7L, 12L);
            mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(error[0]))
                    .andExpect(jsonPath("$.message").value(error[1]));
        }
    }

    @Test void borrowedTitleConflictUsesExistingVietnameseErrorResponse() throws Exception {
        token("READER");
        String message = "Bạn đang mượn đầu sách “Dế Mèn phiêu lưu ký” và chưa trả. "
                + "Vui lòng trả hết các bản đang mượn của đầu sách này trước khi đặt giữ.";
        doThrow(new ApiException(HttpStatus.CONFLICT, "BOOK_ALREADY_BORROWED", message))
                .when(service).reserve(7L, 12L);
        mvc.perform(post("/api/v1/books/7/reservations").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"readerId\":999}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_ALREADY_BORROWED"))
                .andExpect(jsonPath("$.message").value(message));
        verify(service).reserve(7L, 12L);
    }

    @Test void invalidPathUsesExistingValidation() throws Exception {
        token("READER");
        mvc.perform(post("/api/v1/books/abc/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
    }
}
