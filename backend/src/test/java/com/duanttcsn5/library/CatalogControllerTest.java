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
                        .content("{\"title\":\"Sách mới\",\"authorId\":4,\"categoryId\":1,\"publisher\":\"NXB Trẻ\",\"publicationYear\":2024,\"pageCount\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTHOR_INACTIVE"));
    }
    @Test
    @DisplayName("S2-01.1 - POST /api/v1/books từ chối khi bỏ trống trường bắt buộc")
    void catalogBook_MissingRequiredFields_ReturnsBadRequest() throws Exception {
        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sách thiếu dữ liệu\",\"authorId\":1,\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("S2-01.1 - POST /api/v1/books từ chối số trang không hợp lệ")
    void catalogBook_InvalidPageCount_ReturnsBadRequest() throws Exception {
        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sách mới\",\"authorId\":1,\"categoryId\":1,\"publisher\":\"NXB Trẻ\",\"publicationYear\":2024,\"pageCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("Số trang")));
    }


    @Test
    @DisplayName("S2-01.2 - POST /api/v1/books nhận danh sách nhiều tác giả")
    void catalogBook_MultipleAuthors_Success() throws Exception {
        BookResponse response = new BookResponse(
                100L,
                "978-604-S2-012",
                "Sách nhiều tác giả",
                "Nhan đề phụ",
                1L,
                "Nguyễn Nhật Ánh",
                true,
                List.of(
                        new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true),
                        new BookResponse.BookAuthorResponse(2L, "Nam Cao", true)
                ),
                6L,
                "Văn học trong nước",
                true,
                "NXB Trẻ",
                2025,
                320,
                "Tóm tắt",
                OffsetDateTime.now()
        );

        when(bookCatalogService.catalogBook(any(CatalogBookRequest.class), any(), any()))
                .thenReturn(response);

        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Sách nhiều tác giả",
                                  "subtitle":"Nhan đề phụ",
                                  "authorIds":[1,2],
                                  "categoryId":6,
                                  "isbn":"978-604-S2-012",
                                  "publisher":"NXB Trẻ",
                                  "publicationYear":2025,
                                  "pageCount":320,
                                  "description":"Tóm tắt"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authors.length()").value(2))
                .andExpect(jsonPath("$.authors[0].name").value("Nguyễn Nhật Ánh"))
                .andExpect(jsonPath("$.authors[1].name").value("Nam Cao"));
    }

    @Test
    @DisplayName("S2-01.2 - POST /api/v1/books từ chối tác giả trùng lặp")
    void catalogBook_DuplicateAuthors_ReturnsBadRequest() throws Exception {
        when(bookCatalogService.catalogBook(any(CatalogBookRequest.class), any(), any()))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_AUTHOR",
                        "Không thể chọn cùng một tác giả nhiều lần cho một đầu sách."));

        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Sách nhiều tác giả",
                                  "authorIds":[1,1],
                                  "categoryId":6,
                                  "publisher":"NXB Trẻ",
                                  "publicationYear":2025,
                                  "pageCount":320
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_AUTHOR"))
                .andExpect(jsonPath("$.message", containsString("nhiều lần")));
    }

    @Test
    @DisplayName("S2-01.4 - POST /api/v1/books trả về cảnh báo cùng liên kết dữ liệu khi nhan đề trùng")
    void catalogBook_DuplicateTitle_ReturnsConflictWithExistingBook() throws Exception {
        BookResponse existing = new BookResponse(
                55L,
                "1234567890",
                "Tôi thấy hoa vàng trên cỏ xanh",
                null,
                1L,
                "Nguyễn Nhật Ánh",
                true,
                List.of(new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true)),
                6L,
                "Văn học trong nước",
                true,
                "NXB Trẻ",
                2010,
                378,
                "Hồ sơ cũ",
                OffsetDateTime.now()
        );

        when(bookCatalogService.catalogBook(any(CatalogBookRequest.class), any(), any()))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "TITLE_ALREADY_EXISTS",
                        "Nhan đề đã tồn tại trong hệ thống.",
                        java.util.Map.of(
                                "field", "title",
                                "title", "Tôi thấy hoa vàng trên cỏ xanh",
                                "duplicates", List.of(existing),
                                "matchingRule", "So sánh nhan đề sau khi bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/chữ thường."
                        )));

        bookMockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Tôi thấy hoa vàng trên cỏ xanh",
                                  "authorIds":[1],
                                  "categoryId":6,
                                  "publisher":"NXB Trẻ",
                                  "publicationYear":2025,
                                  "pageCount":320,
                                  "confirmDuplicateTitle":false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TITLE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details.field").value("title"))
                .andExpect(jsonPath("$.details.duplicates[0].id").value(55))
                .andExpect(jsonPath("$.details.duplicates[0].title").value("Tôi thấy hoa vàng trên cỏ xanh"));
    }


    @Test
    @DisplayName("S2-01.5 - GET /api/v1/books/public chỉ trả danh mục công khai")
    void getPublicBooks_Success() throws Exception {
        BookResponse visible = new BookResponse(
                88L,
                "9786041234567",
                "Đầu sách đã có bản sao",
                null,
                1L,
                "Nguyễn Nhật Ánh",
                true,
                List.of(new BookResponse.BookAuthorResponse(1L, "Nguyễn Nhật Ánh", true)),
                6L,
                "Văn học trong nước",
                true,
                "NXB Trẻ",
                2025,
                320,
                "Tóm tắt",
                OffsetDateTime.now(),
                1L,
                true
        );

        when(bookCatalogService.getPublicBooks()).thenReturn(List.of(visible));

        bookMockMvc.perform(get("/api/v1/books/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(88))
                .andExpect(jsonPath("$[0].copyCount").value(1))
                .andExpect(jsonPath("$[0].hasCopies").value(true));
    }


}
