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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        bookCatalogService = new BookCatalogService(bookRepository, authorRepository, categoryRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Biên mục sách thành công khi tác giả và thể loại đang hoạt động")
    void catalogBook_Success() {
        Author author = new Author("Nguyễn Nhật Ánh", "Ghi chú", true);
        author.setId(1L);

        Category category = new Category("Văn học trong nước", null, "Mô tả", true);
        category.setId(6L);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category));

        Book saved = new Book("978-604-001", "Tôi thấy hoa vàng trên cỏ xanh", author, category, "NXB Trẻ", 2010, "Mô tả sách");
        saved.setId(100L);
        when(bookRepository.save(any(Book.class))).thenReturn(saved);

        CatalogBookRequest request = new CatalogBookRequest("Tôi thấy hoa vàng trên cỏ xanh", 1L, 6L, "978-604-001", "NXB Trẻ", 2010, "Mô tả sách");
        BookResponse response = bookCatalogService.catalogBook(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Tôi thấy hoa vàng trên cỏ xanh", response.title());
        assertEquals("Nguyễn Nhật Ánh", response.authorName());
        assertEquals("Văn học trong nước", response.categoryName());
        assertTrue(response.authorActive());
        assertTrue(response.categoryActive());

        verify(auditLogRepository).insert(eq(10L), eq("BOOK_CATALOGED"), eq("BOOK"), eq("100"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Từ chối biên mục mới khi tác giả đã ngừng sử dụng")
    void catalogBook_InactiveAuthor_ThrowsBadRequest() {
        Author inactiveAuthor = new Author("Vũ Trọng Phụng", "Ghi chú", false);
        inactiveAuthor.setId(4L);

        when(authorRepository.findById(4L)).thenReturn(Optional.of(inactiveAuthor));

        CatalogBookRequest request = new CatalogBookRequest("Số đỏ (Tái bản mới)", 4L, 6L, "978-001", "NXB Văn học", 2024, "Mô tả");

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1")
        );

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

        CatalogBookRequest request = new CatalogBookRequest("Truyện ngắn Nam Cao", 2L, 4L, "978-002", "NXB Văn học", 2024, "Mô tả");

        ApiException ex = assertThrows(ApiException.class, () ->
                bookCatalogService.catalogBook(request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_INACTIVE", ex.getCode());
        verify(bookRepository, never()).save(any());
    }
}
