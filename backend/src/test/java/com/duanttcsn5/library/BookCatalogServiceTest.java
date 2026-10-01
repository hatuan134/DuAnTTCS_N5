package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import com.duanttcsn5.library.service.BookCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCatalogServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private BookCatalogService bookCatalogService;

    @BeforeEach
    void setUp() {
        bookCatalogService = new BookCatalogService(
                bookRepository,
                authorRepository,
                categoryRepository,
                auditLogRepository);
    }

    @Test
    @DisplayName("S2-01.1 - Tạo đầu sách thành công với dữ liệu thư mục hợp lệ")
    void catalogBook_ValidBasicBibliographicData_Success() {
        Author author = activeAuthor(1L, "Nguyễn Nhật Ánh");
        Category category = activeCategory(6L, "Văn học trong nước");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category));
        when(bookRepository.existsPublisherInCatalog("NXB Trẻ")).thenReturn(true);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(100L);
            return book;
        });

        CatalogBookRequest request = new CatalogBookRequest(
                "Tôi thấy hoa vàng trên cỏ xanh",
                "Một câu chuyện về tuổi thơ",
                1L,
                null,
                6L,
                "9786040001",
                "NXB Trẻ",
                2010,
                378,
                "Mô tả sách");

        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Tôi thấy hoa vàng trên cỏ xanh", response.title());
        assertEquals("Nguyễn Nhật Ánh", response.authorName());
        assertEquals(1, response.authors().size());
        assertEquals("Nguyễn Nhật Ánh", response.authors().get(0).name());
        assertEquals(378, response.pageCount());
        assertTrue(response.authorActive());
        assertTrue(response.categoryActive());
    }

    @Test
    @DisplayName("S2-01.2 - Tạo đầu sách có một tác giả bằng authorIds")
    void catalogBook_OneSelectedAuthor_Success() {
        Author author = activeAuthor(1L, "Nguyễn Nhật Ánh");
        mockCommonCatalogDependencies(List.of(author));

        CatalogBookRequest request = requestWithAuthorIds(List.of(1L));
        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertEquals(1, response.authors().size());
        assertEquals(1L, response.authors().get(0).id());
    }

    @Test
    @DisplayName("S2-01.2 - Tạo đầu sách có nhiều tác giả và lưu đủ danh sách")
    void catalogBook_MultipleSelectedAuthors_Success() {
        Author first = activeAuthor(1L, "Nguyễn Nhật Ánh");
        Author second = activeAuthor(2L, "Nam Cao");
        Author third = activeAuthor(3L, "Tô Hoài");
        mockCommonCatalogDependencies(List.of(first, second, third));

        CatalogBookRequest request = requestWithAuthorIds(List.of(1L, 2L, 3L));
        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertEquals(3, response.authors().size());
        assertEquals(
                List.of("Nam Cao", "Nguyễn Nhật Ánh", "Tô Hoài"),
                response.authors().stream().map(BookResponse.BookAuthorResponse::name).toList());
        verify(bookRepository).save(any(Book.class));
        verify(auditLogRepository).insert(
                eq(10L), eq("BOOK_CATALOGED"), eq("BOOK"), eq("100"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("S2-01.2 - Từ chối cùng một tác giả xuất hiện nhiều lần")
    void catalogBook_DuplicateAuthorIds_ThrowsBadRequest() {
        CatalogBookRequest request = requestWithAuthorIds(List.of(1L, 1L));

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("DUPLICATE_AUTHOR", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.2 - Từ chối khi không chọn tác giả")
    void catalogBook_NoAuthorSelected_ThrowsBadRequest() {
        CatalogBookRequest request = requestWithAuthorIds(List.of());

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("AUTHOR_REQUIRED", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.2 - Từ chối tác giả đã ngừng sử dụng trong danh sách nhiều tác giả")
    void catalogBook_InactiveSelectedAuthor_ThrowsBadRequest() {
        Author active = activeAuthor(1L, "Nguyễn Nhật Ánh");
        Author inactive = new Author("Vũ Trọng Phụng", "Ghi chú", false);
        inactive.setId(4L);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(active));
        when(authorRepository.findById(4L)).thenReturn(Optional.of(inactive));

        CatalogBookRequest request = requestWithAuthorIds(List.of(1L, 4L));

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("AUTHOR_INACTIVE", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.1 - Từ chối năm xuất bản lớn hơn năm hiện tại")
    void catalogBook_FuturePublicationYear_ThrowsBadRequest() {
        int futureYear = Year.now().getValue() + 1;
        CatalogBookRequest request = new CatalogBookRequest(
                "Sách hợp lệ", "Nhan đề phụ", null, null, 1L, null,
                "NXB Trẻ", futureYear, 320, "Tóm tắt", List.of(1L));

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_PUBLICATION_YEAR", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.1 - Từ chối số trang không hợp lệ")
    void catalogBook_InvalidPageCount_ThrowsBadRequest() {
        CatalogBookRequest request = new CatalogBookRequest(
                "Sách hợp lệ", "Nhan đề phụ", null, null, 1L, null,
                "NXB Trẻ", 2020, 0, "Tóm tắt", List.of(1L));

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_PAGE_COUNT", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.3 - Cho phép để trống ISBN")
    void catalogBook_BlankIsbn_Success() {
        mockCommonCatalogDependencies(List.of(activeAuthor(1L, "Nguyễn Nhật Ánh")));

        BookResponse response = bookCatalogService.catalogBook(
                requestWithIsbn("   "), 10L, "127.0.0.1");

        assertNull(response.isbn());
        verify(bookRepository, never()).existsByNormalizedIsbn(anyString());
    }

    @Test
    @DisplayName("S2-01.3 - Cho phép ISBN 10 chữ số")
    void catalogBook_TenDigitIsbn_Success() {
        mockCommonCatalogDependencies(List.of(activeAuthor(1L, "Nguyễn Nhật Ánh")));
        when(bookRepository.existsByNormalizedIsbn("1234567890")).thenReturn(false);

        BookResponse response = bookCatalogService.catalogBook(
                requestWithIsbn("1234567890"), 10L, "127.0.0.1");

        assertEquals("1234567890", response.isbn());
        verify(bookRepository).existsByNormalizedIsbn("1234567890");
    }

    @Test
    @DisplayName("S2-01.3 - Cho phép ISBN 13 chữ số")
    void catalogBook_ThirteenDigitIsbn_Success() {
        mockCommonCatalogDependencies(List.of(activeAuthor(1L, "Nguyễn Nhật Ánh")));
        when(bookRepository.existsByNormalizedIsbn("9786041234567")).thenReturn(false);

        BookResponse response = bookCatalogService.catalogBook(
                requestWithIsbn("9786041234567"), 10L, "127.0.0.1");

        assertEquals("9786041234567", response.isbn());
        verify(bookRepository).existsByNormalizedIsbn("9786041234567");
    }

    @Test
    @DisplayName("S2-01.3 - Từ chối ISBN có 9, 11, 12 hoặc 14 chữ số")
    void catalogBook_InvalidIsbnLength_ThrowsBadRequest() {
        for (String isbn : List.of("123456789", "12345678901", "123456789012", "12345678901234")) {
            ApiException ex = assertThrows(ApiException.class, () ->
                    bookCatalogService.catalogBook(requestWithIsbn(isbn), 10L, "127.0.0.1"));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("INVALID_ISBN_FORMAT", ex.getCode());
            assertEquals("isbn", ex.getDetails().get("field"));
        }
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.3 - Từ chối ISBN chứa chữ cái hoặc ký tự đặc biệt")
    void catalogBook_IsbnContainsNonDigit_ThrowsBadRequest() {
        for (String isbn : List.of("12345ABCDE", "97860412-3456", "97860412 3456")) {
            ApiException ex = assertThrows(ApiException.class, () ->
                    bookCatalogService.catalogBook(requestWithIsbn(isbn), 10L, "127.0.0.1"));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("INVALID_ISBN_FORMAT", ex.getCode());
            assertEquals("isbn", ex.getDetails().get("field"));
        }
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.3 - Từ chối ISBN đã tồn tại")
    void catalogBook_DuplicateIsbn_ThrowsConflict() {
        when(bookRepository.existsByNormalizedIsbn("9786041234567")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(
                        requestWithIsbn("9786041234567"), 10L, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ISBN_ALREADY_EXISTS", ex.getCode());
        assertEquals("isbn", ex.getDetails().get("field"));
        assertEquals("9786041234567", ex.getDetails().get("isbn"));
        verify(bookRepository, never()).save(any());
    }

    private CatalogBookRequest requestWithIsbn(String isbn) {
        return new CatalogBookRequest(
                "Sách kiểm thử ISBN",
                "Nhan đề phụ",
                null,
                null,
                6L,
                isbn,
                "NXB Trẻ",
                2025,
                320,
                "Tóm tắt",
                List.of(1L));
    }

    private CatalogBookRequest requestWithAuthorIds(List<Long> authorIds) {
        return new CatalogBookRequest(
                "Sách nhiều tác giả",
                "Nhan đề phụ",
                null,
                null,
                6L,
                "9786041234567",
                "NXB Trẻ",
                2025,
                320,
                "Tóm tắt",
                authorIds);
    }

    private void mockCommonCatalogDependencies(List<Author> authors) {
        for (Author author : authors) {
            when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));
        }
        Category category = activeCategory(6L, "Văn học trong nước");
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category));
        when(bookRepository.existsPublisherInCatalog("NXB Trẻ")).thenReturn(true);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(100L);
            return book;
        });
    }

    private Author activeAuthor(Long id, String name) {
        Author author = new Author(name, "Ghi chú", true);
        author.setId(id);
        return author;
    }

    private Category activeCategory(Long id, String name) {
        Category category = new Category(name, null, "Mô tả", true);
        category.setId(id);
        return category;
    }
}
