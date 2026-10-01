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
    @DisplayName("S2-01.1 - Tạo đầu sách thành công với đầy đủ dữ liệu thư mục hợp lệ")
    void catalogBook_ValidBasicBibliographicData_Success() {
        Author author = new Author("Nguyễn Nhật Ánh", "Ghi chú", true);
        author.setId(1L);

        Category category = new Category("Văn học trong nước", null, "Mô tả", true);
        category.setId(6L);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category));
        when(bookRepository.existsPublisherInCatalog("NXB Trẻ")).thenReturn(true);

        Book saved = new Book(
                "978-604-001",
                "Tôi thấy hoa vàng trên cỏ xanh",
                "Một câu chuyện về tuổi thơ",
                author,
                category,
                "NXB Trẻ",
                2010,
                378,
                "Mô tả sách");
        saved.setId(100L);
        when(bookRepository.save(any(Book.class))).thenReturn(saved);

        CatalogBookRequest request = new CatalogBookRequest(
                "Tôi thấy hoa vàng trên cỏ xanh",
                "Một câu chuyện về tuổi thơ",
                1L,
                null,
                6L,
                "978-604-001",
                "NXB Trẻ",
                2010,
                378,
                "Mô tả sách");

        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Tôi thấy hoa vàng trên cỏ xanh", response.title());
        assertEquals("Một câu chuyện về tuổi thơ", response.subtitle());
        assertEquals("Nguyễn Nhật Ánh", response.authorName());
        assertEquals("Văn học trong nước", response.categoryName());
        assertEquals("NXB Trẻ", response.publisher());
        assertEquals(2010, response.publicationYear());
        assertEquals(378, response.pageCount());
        assertEquals("Mô tả sách", response.description());
        assertTrue(response.authorActive());
        assertTrue(response.categoryActive());

        verify(auditLogRepository).insert(
                eq(10L), eq("BOOK_CATALOGED"), eq("BOOK"), eq("100"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("S2-01.1 - Từ chối năm xuất bản lớn hơn năm hiện tại")
    void catalogBook_FuturePublicationYear_ThrowsBadRequest() {
        int futureYear = Year.now().getValue() + 1;
        CatalogBookRequest request = validRequest(futureYear, 320);

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_PUBLICATION_YEAR", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.1 - Từ chối số trang không hợp lệ")
    void catalogBook_InvalidPageCount_ThrowsBadRequest() {
        CatalogBookRequest request = validRequest(2020, 0);

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_PAGE_COUNT", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("S2-01.1 - Từ chối nhà xuất bản không có trong danh mục hiện tại")
    void catalogBook_PublisherNotInExistingCatalog_ThrowsBadRequest() {
        Author author = new Author("Nguyễn Nhật Ánh", "Ghi chú", true);
        author.setId(1L);
        Category category = new Category("Văn học", null, "Mô tả", true);
        category.setId(1L);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(bookRepository.existsPublisherInCatalog("NXB Không tồn tại")).thenReturn(false);

        CatalogBookRequest request = new CatalogBookRequest(
                "Sách mới", null, 1L, null, 1L, null,
                "NXB Không tồn tại", 2020, 200, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("PUBLISHER_NOT_IN_CATALOG", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("Danh sách nhà xuất bản lấy từ dữ liệu đầu sách hiện có")
    void getPublisherOptions_ReturnsExistingPublisherValues() {
        when(bookRepository.findDistinctPublishers()).thenReturn(List.of("NXB Kim Đồng", "NXB Trẻ", "NXB Văn học"));

        assertEquals(
                List.of("NXB Kim Đồng", "NXB Trẻ", "NXB Văn học"),
                bookCatalogService.getPublisherOptions());
    }

    @Test
    @DisplayName("Từ chối biên mục mới khi tác giả đã ngừng sử dụng")
    void catalogBook_InactiveAuthor_ThrowsBadRequest() {
        Author inactiveAuthor = new Author("Vũ Trọng Phụng", "Ghi chú", false);
        inactiveAuthor.setId(4L);
        when(authorRepository.findById(4L)).thenReturn(Optional.of(inactiveAuthor));

        CatalogBookRequest request = new CatalogBookRequest(
                "Số đỏ (Tái bản mới)", null, 4L, null, 6L,
                "978-001", "NXB Văn học", 2024, 250, "Mô tả");

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("AUTHOR_INACTIVE", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối biên mục mới khi thể loại đã ngừng sử dụng")
    void catalogBook_InactiveCategory_ThrowsBadRequest() {
        Author author = new Author("Nam Cao", "Ghi chú", true);
        author.setId(2L);
        Category inactiveCategory = new Category("Thể loại cũ ngừng dùng", null, "Mô tả", false);
        inactiveCategory.setId(4L);

        when(authorRepository.findById(2L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(inactiveCategory));

        CatalogBookRequest request = new CatalogBookRequest(
                "Truyện ngắn Nam Cao", null, 2L, null, 4L,
                "978-002", "NXB Văn học", 2024, 280, "Mô tả");

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_INACTIVE", ex.getCode());
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giữ tương thích nghiệp vụ cũ: có thể tự tạo tác giả khi API cũ gửi authorName")
    void catalogBook_NewAuthorName_CreatesAuthor() {
        Category category = new Category("Khoa học", null, "Mô tả", true);
        category.setId(8L);
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        when(authorRepository.findByNameIgnoreCase("Tác giả mới")).thenReturn(Optional.empty());
        when(bookRepository.existsPublisherInCatalog("NXB Trẻ")).thenReturn(true);

        Author savedAuthor = new Author("Tác giả mới", "Tự động tạo khi biên mục đầu sách", true);
        savedAuthor.setId(20L);
        when(authorRepository.save(any(Author.class))).thenReturn(savedAuthor);

        Book savedBook = new Book(
                "978-003", "Sách mới", null, savedAuthor, category,
                "NXB Trẻ", 2026, 190, "Mô tả");
        savedBook.setId(200L);
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        CatalogBookRequest request = new CatalogBookRequest(
                "Sách mới", null, null, "Tác giả mới", 8L,
                "978-003", "NXB Trẻ", 2026, 190, "Mô tả");

        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertEquals("Tác giả mới", response.authorName());
        verify(authorRepository).save(any(Author.class));
        verify(auditLogRepository).insert(
                eq(10L), eq("AUTHOR_CREATED"), eq("AUTHOR"), eq("20"), anyString(), eq("127.0.0.1"));
    }

    private CatalogBookRequest validRequest(int publicationYear, int pageCount) {
        return new CatalogBookRequest(
                "Sách hợp lệ",
                "Nhan đề phụ",
                1L,
                null,
                1L,
                null,
                "NXB Trẻ",
                publicationYear,
                pageCount,
                "Tóm tắt");
    }
}
