package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.exception.GlobalExceptionHandler;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookPublicDetailTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCopyRepository bookCopyRepository;
    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private BookCatalogService service;
    private MockMvc mockMvc;

    private Author primaryAuthor;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        service = new BookCatalogService(
                bookRepository,
                bookCopyRepository,
                authorRepository,
                categoryRepository,
                auditLogRepository);

        BookCatalogController controller = new BookCatalogController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        primaryAuthor = new Author("Nguyễn Nhật Ánh", null, true);
        primaryAuthor.setId(1L);

        testCategory = new Category("Văn học trong nước", null, null, true);
        testCategory.setId(6L);
    }

    @Test
    @DisplayName("S2-06.1 - Mở đầu sách có ảnh bìa và đầy đủ thông tin thư mục")
    void bookWithCoverImageAndFullBibliographicDetails() {
        Book book = createBook(101L, "Cho tôi xin một vé đi tuổi thơ", "Tập 1", "978-604-2-00001-1",
                "NXB Trẻ", 2008, 250, "Tác phẩm nổi tiếng về tuổi thơ", "https://example.com/cover.jpg");

        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(5L);
        when(bookCopyRepository.countAvailableByBookId(101L)).thenReturn(3L);

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(101L, result.id());
        assertEquals("Cho tôi xin một vé đi tuổi thơ", result.title());
        assertEquals("Tập 1", result.subtitle());
        assertEquals("978-604-2-00001-1", result.isbn());
        assertEquals("Nguyễn Nhật Ánh", result.authorName());
        assertEquals("NXB Trẻ", result.publisher());
        assertEquals(2008, result.publicationYear());
        assertEquals(250, result.pageCount());
        assertEquals("Văn học trong nước", result.categoryName());
        assertEquals("Tác phẩm nổi tiếng về tuổi thơ", result.description());
        assertEquals("https://example.com/cover.jpg", result.coverImageUrl());
        assertEquals(5L, result.copyCount());
        assertEquals(3L, result.availableCount());
    }

    @Test
    @DisplayName("S2-06.1 - Mở đầu sách không có ảnh bìa: coverImageUrl là null")
    void bookWithoutCoverImageReturnsNullCoverImageUrl() {
        Book book = createBook(102L, "Mắt biếc", null, "978-604-2-00002-2",
                "NXB Trẻ", 1990, 180, "Tiểu thuyết tình cảm", null);

        when(bookRepository.findPublicByIdWithAuthorAndCategory(102L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.countByBookId(102L)).thenReturn(2L);
        when(bookCopyRepository.countAvailableByBookId(102L)).thenReturn(1L);

        BookResponse result = service.getPublicBookById(102L);

        assertNull(result.coverImageUrl());
        assertEquals("Mắt biếc", result.title());
        assertNull(result.subtitle());
    }

    @Test
    @DisplayName("S2-06.1 - Đầu sách không có bản sao nào: copyCount=0, availableCount=0")
    void bookWithoutCopiesReturnsZeroCounts() {
        Book book = createBook(103L, "Sách mới nhập hồ sơ", null, "978-604-2-00003-3",
                "NXB Giáo Dục", 2026, 300, "Đang chuẩn bị bản sao", null);

        when(bookRepository.findPublicByIdWithAuthorAndCategory(103L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.countByBookId(103L)).thenReturn(0L);
        when(bookCopyRepository.countAvailableByBookId(103L)).thenReturn(0L);

        BookResponse result = service.getPublicBookById(103L);

        assertEquals(0L, result.copyCount());
        assertEquals(0L, result.availableCount());
        assertFalse(result.hasCopies());
    }

    @Test
    @DisplayName("S2-06.1 - Đầu sách có nhiều trạng thái bản sao: tổng số bản là toàn bộ bản sao, chỉ tính bản AVAILABLE là Sẵn sàng")
    void bookWithMultipleCopyStatusesCountsTotalAndAvailableCorrectly() {
        Book book = createBook(104L, "Tôi thấy hoa vàng trên cỏ xanh", null, "978-604-2-00004-4",
                "NXB Trẻ", 2010, 320, "Truyện dài", "https://example.com/hoa-vang.jpg");

        // Giả sử có 8 bản sao: 2 AVAILABLE, 1 BORROWED, 1 HELD, 1 REPAIR, 1 REMOVED, 1 LOST, 1 DAMAGED
        // Tổng số bản sao trong hệ thống là 8, nhưng chỉ có 2 bản AVAILABLE
        when(bookRepository.findPublicByIdWithAuthorAndCategory(104L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.countByBookId(104L)).thenReturn(8L);
        when(bookCopyRepository.countAvailableByBookId(104L)).thenReturn(2L);

        BookResponse result = service.getPublicBookById(104L);

        assertEquals(8L, result.copyCount());
        assertEquals(2L, result.availableCount());
    }

    @Test
    @DisplayName("S2-06.1 - Không tìm thấy đầu sách ném ra 404 PUBLIC_BOOK_NOT_FOUND")
    void bookNotFoundThrows404() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(999L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getPublicBookById(999L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("PUBLIC_BOOK_NOT_FOUND", exception.getCode());
    }

    @Test
    @DisplayName("S2-06.1 - Controller GET /public/{id} trả về dữ liệu và KHÔNG chứa thông tin người đang mượn")
    void getPublicBookByIdControllerReturnsCleanDataWithoutBorrowerInfo() throws Exception {
        Book book = createBook(101L, "Cho tôi xin một vé đi tuổi thơ", "Bản đặc biệt", "978-604-2-00001-1",
                "NXB Trẻ", 2008, 250, "Mô tả nội dung", "https://example.com/cover.jpg");

        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(4L);
        when(bookCopyRepository.countAvailableByBookId(101L)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/books/public/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.title").value("Cho tôi xin một vé đi tuổi thơ"))
                .andExpect(jsonPath("$.subtitle").value("Bản đặc biệt"))
                .andExpect(jsonPath("$.isbn").value("978-604-2-00001-1"))
                .andExpect(jsonPath("$.authorName").value("Nguyễn Nhật Ánh"))
                .andExpect(jsonPath("$.publisher").value("NXB Trẻ"))
                .andExpect(jsonPath("$.publicationYear").value(2008))
                .andExpect(jsonPath("$.pageCount").value(250))
                .andExpect(jsonPath("$.categoryName").value("Văn học trong nước"))
                .andExpect(jsonPath("$.description").value("Mô tả nội dung"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/cover.jpg"))
                .andExpect(jsonPath("$.copyCount").value(4))
                .andExpect(jsonPath("$.availableCount").value(1))
                // Đảm bảo tuyệt đối không có thông tin người mượn trong response
                .andExpect(jsonPath("$.borrowerName").doesNotExist())
                .andExpect(jsonPath("$.borrowerId").doesNotExist())
                .andExpect(jsonPath("$.borrowers").doesNotExist())
                .andExpect(jsonPath("$.loans").doesNotExist())
                .andExpect(jsonPath("$.shelfLocation").doesNotExist());
    }

    private Book createBook(Long id, String title, String subtitle, String isbn,
                            String publisher, Integer publicationYear, Integer pageCount,
                            String description, String coverImageUrl) {
        Book book = new Book(isbn, title, subtitle, primaryAuthor, testCategory,
                publisher, publicationYear, pageCount, description);
        book.setId(id);
        book.setCoverImageUrl(coverImageUrl);
        return book;
    }
}
