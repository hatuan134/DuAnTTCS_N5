package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.AuthorController;
import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.controller.CategoryController;
import com.duanttcsn5.library.dto.author.AuthorResponse;
import com.duanttcsn5.library.dto.author.CreateAuthorRequest;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.dto.category.CategoryResponse;
import com.duanttcsn5.library.dto.category.CreateCategoryRequest;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.service.AuthorService;
import com.duanttcsn5.library.service.BookCatalogService;
import com.duanttcsn5.library.service.CategoryService;
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
class CatalogControllerTest {

    private MockMvc authorMockMvc;
    private MockMvc categoryMockMvc;
    private MockMvc bookMockMvc;

    @Mock
    private AuthorService authorService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private BookCatalogService bookCatalogService;

    @InjectMocks
    private AuthorController authorController;

    @InjectMocks
    private CategoryController categoryController;

    @InjectMocks
    private BookCatalogController bookCatalogController;

    @BeforeEach
    void setUp() {
        authorMockMvc = MockMvcBuilders.standaloneSetup(authorController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        categoryMockMvc = MockMvcBuilders.standaloneSetup(categoryController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        bookMockMvc = MockMvcBuilders.standaloneSetup(bookCatalogController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/authors trả về danh sách tác giả")
    void getAllAuthors_Success() throws Exception {
        AuthorResponse res = new AuthorResponse(1L, "Nguyễn Nhật Ánh", "Ghi chú", true, 12L, OffsetDateTime.now(), OffsetDateTime.now());
        when(authorService.getAllAuthors()).thenReturn(List.of(res));

        authorMockMvc.perform(get("/api/v1/authors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Nguyễn Nhật Ánh"))
                .andExpect(jsonPath("$[0].bookCount").value(12));
    }

    @Test
    @DisplayName("POST /api/v1/authors tạo tác giả thành công")
    void createAuthor_Success() throws Exception {
        AuthorResponse res = new AuthorResponse(1L, "Nguyễn Nhật Ánh", "Ghi chú", true, 0L, OffsetDateTime.now(), OffsetDateTime.now());
        when(authorService.createAuthor(any(CreateAuthorRequest.class), any(), any())).thenReturn(res);

        authorMockMvc.perform(post("/api/v1/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nguyễn Nhật Ánh\",\"note\":\"Ghi chú\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Nguyễn Nhật Ánh"));
    }

    @Test
    @DisplayName("POST /api/v1/authors trả về 409 khi tên tác giả bị trùng")
    void createAuthor_Duplicate_ThrowsConflict() throws Exception {
        when(authorService.createAuthor(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "AUTHOR_NAME_EXISTS", "Tên tác giả 'Nguyễn Nhật Ánh' đã tồn tại trong danh mục tác giả."));

        authorMockMvc.perform(post("/api/v1/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nguyễn Nhật Ánh\",\"note\":\"Ghi chú\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTHOR_NAME_EXISTS"))
                .andExpect(jsonPath("$.message", containsString("đã tồn tại")));
    }

    @Test
    @DisplayName("DELETE /api/v1/authors/{id} trả về 400 khi tác giả đang gắn với đầu sách")
    void deleteAuthor_InUse_ThrowsBadRequest() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_IN_USE", "Không thể xoá tác giả vì đang gắn với 12 đầu sách."))
                .when(authorService).deleteAuthor(eq(1L), any(), any());

        authorMockMvc.perform(delete("/api/v1/authors/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTHOR_IN_USE"))
                .andExpect(jsonPath("$.message", containsString("12 đầu sách")));
    }

    @Test
    @DisplayName("GET /api/v1/categories/active trả về danh sách thể loại đang hoạt động")
    void getActiveCategories_Success() throws Exception {
        CategoryResponse res = new CategoryResponse(1L, "Văn học", "Mô tả", null, null, 1, true, 25L, OffsetDateTime.now(), OffsetDateTime.now());
        when(categoryService.getActiveCategories()).thenReturn(List.of(res));

        categoryMockMvc.perform(get("/api/v1/categories/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Văn học"))
                .andExpect(jsonPath("$[0].level").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/categories trả về 400 khi xếp lồng quá 2 cấp")
    void createCategory_MaxDepthExceeded_ThrowsBadRequest() throws Exception {
        when(categoryService.createCategory(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_MAX_DEPTH_EXCEEDED", "Thể loại chỉ được phép xếp lồng tối đa 2 cấp."));

        categoryMockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cấp 3\",\"description\":\"\",\"parentId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_MAX_DEPTH_EXCEEDED"));
    }

    @Test
    @DisplayName("POST /api/v1/books trả về 400 khi biên mục với tác giả đã ngừng sử dụng")
    void catalogBook_InactiveAuthor_ThrowsBadRequest() throws Exception {
        when(bookCatalogService.catalogBook(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_INACTIVE", "Không thể biên mục sách với tác giả đã ngừng sử dụng."));

        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sách mới\",\"authorId\":4,\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTHOR_INACTIVE"));
    }
}
