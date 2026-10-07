package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCopyController;
import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.dto.bookcopy.BookCopySummaryResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.BookCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookCopySummaryControllerTest {
    private BookCopyService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(BookCopyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookCopyController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void returnsCountAndRowsInTheSameResponse() throws Exception {
        when(service.getSummaryByBookId(1L)).thenReturn(new BookCopySummaryResponse(
                List.of(copy(1L, "AVAILABLE", "Sẵn sàng"), copy(2L, "HELD", "Đang giữ cho đặt trước")), 1L, 1L, 3L));
        mvc.perform(get("/api/v1/books/1/copies/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(1))
                .andExpect(jsonPath("$.originalCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.copies.length()").value(2))
                .andExpect(jsonPath("$.copies[0].bookId").value(1))
                .andExpect(jsonPath("$.copies[1].status").value("HELD"));
    }

    @Test void serializesZeroAsANumberAndCopiesAsAnEmptyArray() throws Exception {
        when(service.getSummaryByBookId(1L)).thenReturn(new BookCopySummaryResponse(List.of(), 0L, 1L, 1L));
        mvc.perform(get("/api/v1/books/1/copies/summary"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"copies\":[],\"availableCount\":0,\"originalCount\":1,\"totalCount\":1}"));
    }

    @Test void missingBookUsesExistingErrorResponse() throws Exception {
        when(service.getSummaryByBookId(999L)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        mvc.perform(get("/api/v1/books/999/copies/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test void invalidNumericBookIdUsesExistingErrorResponse() throws Exception {
        when(service.getSummaryByBookId(0L)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ."));
        mvc.perform(get("/api/v1/books/0/copies/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_ID"));
    }

    @Test void malformedBookIdIsRejectedBeforeService() throws Exception {
        mvc.perform(get("/api/v1/books/abc/copies/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
    }

    private BookCopyResponse copy(Long id, String status, String label) {
        return new BookCopyResponse(id, "TEST-" + id, 1L, "Đầu sách A", null,
                10L, "KHO-A", "Kho A", 20L, "A01", "Kệ A01",
                null, null, null, "Chưa ghi nhận", status, label, null);
    }
}
