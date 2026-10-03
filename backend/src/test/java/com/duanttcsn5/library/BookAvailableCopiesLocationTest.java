package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookAvailableCopyLocationResponse;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookAvailableCopiesLocationTest {

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

    private Book sampleBook;

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

        Author author = new Author("Nguyễn Nhật Ánh", null, true);
        author.setId(1L);

        Category category = new Category("Văn học", null, null, true);
        category.setId(6L);

        sampleBook = new Book("978-604-2-00001-1", "Cho tôi xin một vé đi tuổi thơ", null, author, category,
                "NXB Trẻ", 2008, 200, "Mô tả sách");
        sampleBook.setId(101L);
    }

    @Test
    @DisplayName("S2-06.2 - Có một bản Sẵn sàng: hiển thị đúng kho và kệ của bản đó")
    void singleAvailableCopyLocation() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(1L);

        // 1 bản AVAILABLE tại Kho A, Kệ A01
        Object[] copy1 = new Object[]{1L, "BC-1001", 10L, "KHO-A", "Kho Tổng Hợp", 20L, "A01", "Kệ Văn Học 1"};
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of(copy1));

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(1L, result.availableCount());
        assertNotNull(result.availableCopies());
        assertEquals(1, result.availableCopies().size());

        BookAvailableCopyLocationResponse location = result.availableCopies().get(0);
        assertEquals(1L, location.copyId());
        assertEquals("BC-1001", location.barcode());
        assertEquals("Kho Tổng Hợp", location.warehouseName());
        assertEquals("KHO-A", location.warehouseCode());
        assertEquals("A01", location.shelfCode());
        assertEquals("Kệ Văn Học 1", location.shelfName());
    }

    @Test
    @DisplayName("S2-06.2 - Có nhiều bản Sẵn sàng ở cùng một kho")
    void multipleAvailableCopiesInSameWarehouse() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(3L);

        // 2 bản AVAILABLE cùng ở Kho Tổng Hợp (Kệ A01 và Kệ A02)
        Object[] copy1 = new Object[]{1L, "BC-1001", 10L, "KHO-A", "Kho Tổng Hợp", 20L, "A01", "Kệ 1"};
        Object[] copy2 = new Object[]{2L, "BC-1002", 10L, "KHO-A", "Kho Tổng Hợp", 21L, "A02", "Kệ 2"};
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of(copy1, copy2));

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(2L, result.availableCount());
        assertEquals(2, result.availableCopies().size());
        assertEquals("Kho Tổng Hợp", result.availableCopies().get(0).warehouseName());
        assertEquals("Kho Tổng Hợp", result.availableCopies().get(1).warehouseName());
        assertEquals("A01", result.availableCopies().get(0).shelfCode());
        assertEquals("A02", result.availableCopies().get(1).shelfCode());
    }

    @Test
    @DisplayName("S2-06.2 - Có nhiều bản Sẵn sàng ở các kho khác nhau")
    void multipleAvailableCopiesInDifferentWarehouses() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(4L);

        // Bản 1 ở Kho Tổng Hợp, bản 2 ở Kho Đọc Mở
        Object[] copy1 = new Object[]{1L, "BC-1001", 10L, "KHO-A", "Kho Tổng Hợp", 20L, "A01", "Kệ 1"};
        Object[] copy2 = new Object[]{3L, "BC-1003", 11L, "KHO-B", "Kho Đọc Mở", 30L, "B05", "Kệ 5"};
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of(copy1, copy2));

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(2L, result.availableCount());
        assertEquals(2, result.availableCopies().size());
        assertEquals("Kho Tổng Hợp", result.availableCopies().get(0).warehouseName());
        assertEquals("Kho Đọc Mở", result.availableCopies().get(1).warehouseName());
    }

    @Test
    @DisplayName("S2-06.2 - Có cả bản Sẵn sàng và Đang mượn / Sửa chữa / Đã loại khỏi kho: chỉ trả về vị trí bản Sẵn sàng")
    void onlyAvailableCopiesReturnedWhenOthersAreBorrowedOrUnavailable() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(sampleBook));
        // Tổng số bản sao là 5: 1 AVAILABLE, 1 BORROWED, 1 HELD, 1 REPAIR, 1 REMOVED
        when(bookCopyRepository.countByBookId(101L)).thenReturn(5L);

        // SQL repository chỉ lọc bản AVAILABLE không có loan_items chưa trả, nên chỉ trả về bản copy 1
        Object[] copy1 = new Object[]{1L, "BC-1001", 10L, "KHO-A", "Kho Tổng Hợp", 20L, "A01", "Kệ 1"};
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of(copy1));

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(5L, result.copyCount());
        assertEquals(1L, result.availableCount());
        assertEquals(1, result.availableCopies().size());
        assertEquals("BC-1001", result.availableCopies().get(0).barcode());
    }

    @Test
    @DisplayName("S2-06.2 - Không còn bản Sẵn sàng nào: danh sách vị trí rỗng")
    void noAvailableCopiesReturnsEmptyLocationList() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(101L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(101L)).thenReturn(3L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of());
        when(bookCopyRepository.countAvailableByBookId(101L)).thenReturn(0L);

        BookResponse result = service.getPublicBookById(101L);

        assertEquals(0L, result.availableCount());
        assertTrue(result.availableCopies().isEmpty());
    }

    @Test
    @DisplayName("S2-06.2 - Controller GET /public/{id}/available-copies trả về danh sách vị trí không chứa thông tin người mượn")
    void getAvailableCopiesEndpointReturnsLocationsWithoutBorrowerDetails() throws Exception {
        when(bookRepository.existsById(101L)).thenReturn(true);

        Object[] copy1 = new Object[]{1L, "BC-1001", 10L, "KHO-A", "Kho Tổng Hợp", 20L, "A01", "Kệ Văn Học"};
        Object[] copy2 = new Object[]{2L, "BC-1002", 10L, "KHO-A", "Kho Tổng Hợp", 21L, "A02", "Kệ Thiếu Nhi"};
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(101L)).thenReturn(List.<Object[]>of(copy1, copy2));

        mockMvc.perform(get("/api/v1/books/public/101/available-copies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].copyId").value(1))
                .andExpect(jsonPath("$[0].barcode").value("BC-1001"))
                .andExpect(jsonPath("$[0].warehouseName").value("Kho Tổng Hợp"))
                .andExpect(jsonPath("$[0].shelfCode").value("A01"))
                .andExpect(jsonPath("$[0].shelfName").value("Kệ Văn Học"))
                .andExpect(jsonPath("$[1].copyId").value(2))
                .andExpect(jsonPath("$[1].barcode").value("BC-1002"))
                // Tuyệt đối không chứa thông tin người mượn
                .andExpect(jsonPath("$[0].borrowerName").doesNotExist())
                .andExpect(jsonPath("$[0].borrowerId").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }
}
