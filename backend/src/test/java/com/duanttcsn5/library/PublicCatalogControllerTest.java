package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.BookCatalogService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCatalogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookCatalogService bookCatalogService;

    @InjectMocks
    private BookCatalogController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("S2-05.1 - GET public nhận từ khóa và trả danh sách kết quả")
    void searchPublicBooks() throws Exception {
        when(bookCatalogService.getPublicBooks("nguyen nhat anh", null, null, false))
                .thenReturn(List.of(bookResponse()));

        mockMvc.perform(get("/api/v1/books/public")
                        .param("keyword", "nguyen nhat anh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Cho tôi xin một vé đi tuổi thơ"))
                .andExpect(jsonPath("$[0].authorName").value("Nguyễn Nhật Ánh"))
                .andExpect(jsonPath("$[0].isbn").value("978-604-2-00001-1"))
                .andExpect(jsonPath("$[0].categoryName").value("Văn học trong nước"))
                .andExpect(jsonPath("$[0].publicationYear").value(2008))
                .andExpect(jsonPath("$[0].availableCount").value(2));
    }

    @Test
    @DisplayName("S2-05.1 - GET chi tiết public trả về đầu sách")
    void getPublicBookDetail() throws Exception {
        when(bookCatalogService.getPublicBookById(101L)).thenReturn(bookResponse());

        mockMvc.perform(get("/api/v1/books/public/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.availableCount").value(2));
    }

    private BookResponse bookResponse() {
        return new BookResponse(
                101L,
                "978-604-2-00001-1",
                "Cho tôi xin một vé đi tuổi thơ",
                null,
                1L,
                "Nguyễn Nhật Ánh",
                true,
                List.of(new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true)),
                6L,
                "Văn học trong nước",
                true,
                "NXB Trẻ",
                2008,
                200,
                "Mô tả",
                OffsetDateTime.now(),
                3L,
                true,
                2L);
    }
}
