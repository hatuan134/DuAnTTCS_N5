package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCatalogSearchServiceTest {

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
    private Book childhoodTicket;
    private Book blueEyes;

    @BeforeEach
    void setUp() {
        service = new BookCatalogService(
                bookRepository,
                bookCopyRepository,
                authorRepository,
                categoryRepository,
                auditLogRepository);

        Author nguyenNhatAnh = author(1L, "Nguyễn Nhật Ánh");
        Category literature = category(6L, "Văn học trong nước");

        childhoodTicket = book(
                101L,
                "978-604-2-00001-1",
                "Cho tôi xin một vé đi tuổi thơ",
                nguyenNhatAnh,
                literature,
                2008);

        blueEyes = book(
                102L,
                "9786042000022",
                "Mắt biếc",
                nguyenNhatAnh,
                literature,
                1990);

        lenient().when(bookRepository.findAllPublicWithAuthorAndCategory())
                .thenReturn(List.of(childhoodTicket, blueEyes));
        lenient().when(bookCopyRepository.countAllGroupedByBookId())
                .thenReturn(List.of(new Object[]{101L, 3L}, new Object[]{102L, 2L}));
        lenient().when(bookCopyRepository.countAvailableGroupedByBookId())
                .thenReturn(List.of(new Object[]{101L, 2L}, new Object[]{102L, 1L}));
    }

    @Test
    @DisplayName("S2-05.1 - Tìm theo toàn bộ nhan đề tiếng Việt không dấu")
    void searchByFullTitleWithoutDiacritics() {
        List<BookResponse> result = service.getPublicBooks("Cho toi xin mot ve di tuoi tho");

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).id());
        assertEquals(2L, result.get(0).availableCount());
    }

    @Test
    @DisplayName("S2-05.1 - Tìm theo một phần nhan đề")
    void searchByPartialTitle() {
        List<BookResponse> result = service.getPublicBooks("vé đi tuổi");

        assertEquals(1, result.size());
        assertEquals("Cho tôi xin một vé đi tuổi thơ", result.get(0).title());
    }

    @Test
    @DisplayName("S2-05.1 - Tìm theo tên tác giả không phân biệt hoa thường và dấu")
    void searchByAuthorCaseAndAccentInsensitive() {
        List<BookResponse> result = service.getPublicBooks("NGUYEN NHAT ANH");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("S2-05.1 - Tìm theo ISBN bỏ qua dấu phân cách")
    void searchByIsbnIgnoringSeparators() {
        List<BookResponse> result = service.getPublicBooks("9786042000011");

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).id());
    }

    @Test
    @DisplayName("S2-05.1 - Tìm bằng chữ hoa và chữ thường cho cùng kết quả")
    void searchIsCaseInsensitive() {
        List<BookResponse> lower = service.getPublicBooks("mắt biếc");
        List<BookResponse> mixed = service.getPublicBooks("MẮT BIẾC");

        assertEquals(List.of(102L), lower.stream().map(BookResponse::id).toList());
        assertEquals(List.of(102L), mixed.stream().map(BookResponse::id).toList());
    }

    @Test
    @DisplayName("S2-05.1 - Từ khóa trống trả về toàn bộ đầu sách công khai")
    void blankKeywordReturnsAllPublicBooks() {
        List<BookResponse> result = service.getPublicBooks("   ");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("S2-05.1 - Đầu sách không có bản sao không xuất hiện trong kết quả public")
    void bookWithoutCopiesDoesNotAppearInPublicResults() {
        Book hiddenBook = book(
                999L,
                "9786042999999",
                "Đầu sách chưa được công khai",
                author(9L, "Tác giả ẩn"),
                category(6L, "Văn học trong nước"),
                2025);

        when(bookRepository.findAllPublicWithAuthorAndCategory())
                .thenReturn(List.of(childhoodTicket, blueEyes, hiddenBook));

        List<BookResponse> result = service.getPublicBooks("dau sach chua duoc cong khai");

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("S2-05.1 - Chi tiết public trả về số bản rảnh")
    void publicDetailReturnsAvailableCount() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L))
                .thenReturn(Optional.of(childhoodTicket));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(3L);
        when(bookCopyRepository.countAvailableByBookId(101L)).thenReturn(2L);

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(101L, result.id());
        assertEquals(3L, result.copyCount());
        assertEquals(2L, result.availableCount());
    }

    @Test
    @DisplayName("S2-05.1 - Đầu sách không được công khai không mở được trang chi tiết public")
    void nonPublicBookIsHiddenFromPublicDetail() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(999L))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getPublicBookById(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("PUBLIC_BOOK_NOT_FOUND", exception.getCode());
    }

    private Author author(Long id, String name) {
        Author author = new Author(name, null, true);
        author.setId(id);
        return author;
    }

    private Category category(Long id, String name) {
        Category category = new Category(name, null, null, true);
        category.setId(id);
        return category;
    }

    private Book book(Long id,
                      String isbn,
                      String title,
                      Author author,
                      Category category,
                      Integer publicationYear) {
        Book book = new Book(
                isbn,
                title,
                null,
                author,
                category,
                "NXB Trẻ",
                publicationYear,
                200,
                "Mô tả");
        book.setId(id);
        return book;
    }
}
