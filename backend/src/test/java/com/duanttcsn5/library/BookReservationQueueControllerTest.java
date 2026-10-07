package com.duanttcsn5.library;

import com.duanttcsn5.library.config.SecurityConfig;
import com.duanttcsn5.library.controller.BookReservationController;
import com.duanttcsn5.library.dto.book.BookReservationQueueResponse;
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
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookReservationQueueControllerTest {
    private static final String URL = "/api/v1/books/7/reservations";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, BookReservationController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
    static class Config {
        @Bean BookReservationService reservations() { return mock(BookReservationService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private BookReservationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        service = context.getBean(BookReservationService.class);
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

    @Test
    void guestAndInvalidTokenCannotReadQueue() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        when(context.getBean(JwtService.class).decode("bad-token")).thenThrow(new JwtException("invalid"));
        mvc.perform(get(URL).header("Authorization", "Bearer bad-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void readerCannotReadOtherReadersQueue() throws Exception {
        token("READER");
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void librarianManagerAndAdminCanReadEmptyQueue() throws Exception {
        when(service.getQueueByBookId(7L, null)).thenReturn(new BookReservationQueueResponse(7L, "Mắt biếc", List.of()));
        for (String role : new String[]{"LIBRARIAN", "LIBRARY_MANAGER", "ADMIN"}) {
            token(role);
            mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(7))
                    .andExpect(jsonPath("$.bookTitle").value("Mắt biếc"))
                    .andExpect(jsonPath("$.items").isEmpty());
        }
        verify(service, times(3)).getQueueByBookId(7L, null);
    }

    @Test
    void payloadContainsAllStatusesPositionsAndAllocatedBarcodeWithoutAccountSecrets() throws Exception {
        token("LIBRARIAN");
        var created = OffsetDateTime.parse("2026-10-03T08:00:00+07:00");
        when(service.getQueueByBookId(7L, null)).thenReturn(new BookReservationQueueResponse(7L, "Mắt biếc", List.of(
                new BookReservationQueueResponse.QueueEntry(21L, 12L, "Nguyễn Văn An", created,
                        "READY_FOR_PICKUP", null, 101L, "LIB-101"),
                new BookReservationQueueResponse.QueueEntry(22L, 13L, "Trần Bình", created.plusHours(1),
                        "PENDING", 1L, null, null),
                new BookReservationQueueResponse.QueueEntry(23L, 14L, "Lê Chi", created.plusHours(2),
                        "CANCELLED", null, null, null))));
        mvc.perform(get(URL).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(21))
                .andExpect(jsonPath("$.items[0].readerName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.items[0].status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.items[0].barcode").value("LIB-101"))
                .andExpect(jsonPath("$.items[0].copyId").value(101))
                .andExpect(jsonPath("$.items[0].queuePosition").doesNotExist())
                .andExpect(jsonPath("$.items[1].queuePosition").value(1))
                .andExpect(jsonPath("$.items[1].readerName").value("Trần Bình"))
                .andExpect(jsonPath("$.items[1].reservedAt").exists())
                .andExpect(jsonPath("$.items[1].barcode").doesNotExist())
                .andExpect(jsonPath("$.items[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].reader").doesNotExist());
    }

    @Test
    void statusIsForwardedButBookQueryParameterCannotSelectAnotherBook() throws Exception {
        token("LIBRARIAN");
        when(service.getQueueByBookId(7L, "PENDING")).thenReturn(new BookReservationQueueResponse(7L, "Mắt biếc", List.of()));
        mvc.perform(get(URL).queryParam("bookId", "99").queryParam("status", "PENDING")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(7));
        verify(service).getQueueByBookId(7L, "PENDING");
        verifyNoMoreInteractions(service);
    }

    @Test
    void invalidIdAndMissingBookUseVietnameseErrors() throws Exception {
        token("LIBRARIAN");
        mvc.perform(get("/api/v1/books/abc/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
        when(service.getQueueByBookId(0L, null)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ."));
        mvc.perform(get("/api/v1/books/0/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mã đầu sách không hợp lệ."));
        when(service.getQueueByBookId(99L, null)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        mvc.perform(get("/api/v1/books/99/reservations").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void fiveFiltersAndClearingFilterAreForwardedWithStaffPermissions() throws Exception {
        token("LIBRARIAN");
        for (String filter : new String[]{"PENDING", "READY_FOR_PICKUP", "FULFILLED", "CANCELLED", "EXPIRED", ""}) {
            when(service.getQueueByBookId(7L, filter)).thenReturn(
                    new BookReservationQueueResponse(7L, "Mắt biếc", List.of()));
            mvc.perform(get(URL).queryParam("status", filter).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            verify(service).getQueueByBookId(7L, filter);
        }
        token("READER");
        mvc.perform(get(URL).queryParam("status", "PENDING").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verify(service, times(1)).getQueueByBookId(7L, "PENDING");
    }

    @Test
    void invalidFilterUsesExistingVietnameseErrorResponse() throws Exception {
        token("LIBRARIAN");
        when(service.getQueueByBookId(7L, "UNKNOWN")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_RESERVATION_STATUS", "Trạng thái lọc không hợp lệ."));
        mvc.perform(get(URL).queryParam("status", "UNKNOWN").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATUS"))
                .andExpect(jsonPath("$.message").value("Trạng thái lọc không hợp lệ."));
    }
}
