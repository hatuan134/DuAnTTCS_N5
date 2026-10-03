package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.BookCatalogController;
import com.duanttcsn5.library.dto.book.BookQueueInfoResponse;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookReservationRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import com.duanttcsn5.library.service.BookCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookQueueAndExpectedReturnTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Mock
    private BookReservationRepository bookReservationRepository;

    private BookCatalogService service;
    private MockMvc mockMvc;

    private Book sampleBook;

    @BeforeEach
    void setup() {
        service = new BookCatalogService(
                bookRepository,
                bookCopyRepository,
                authorRepository,
                categoryRepository,
                auditLogRepository,
                bookReservationRepository
        );
        BookCatalogController controller = new BookCatalogController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        sampleBook = new Book();
        ReflectionTestUtils.setField(sampleBook, "id", 10L);
        ReflectionTestUtils.setField(sampleBook, "title", "Không gia đình");
        ReflectionTestUtils.setField(sampleBook, "isbn", "978-604-2-99999-9");

        Author author = new Author("Hector Malot", null, true);
        author.setId(1L);
        ReflectionTestUtils.setField(sampleBook, "author", author);

        Category category = new Category("Văn học cổ điển", null, null, true);
        category.setId(1L);
        ReflectionTestUtils.setField(sampleBook, "category", category);
    }

    @Test
    @DisplayName("Test 1: Không còn bản rảnh và không có người đặt giữ (queueCount = 0)")
    void testNoAvailableCopiesAndNoReservationQueue() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(10L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(10L)).thenReturn(1L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(10L)).thenReturn(List.of());
        when(bookCopyRepository.countAvailableByBookId(10L)).thenReturn(0L);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(0L);
        LocalDate returnDate = LocalDate.now(ZONE).plusDays(5);
        Timestamp dueTs = Timestamp.valueOf(returnDate.atStartOfDay());
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{dueTs, Timestamp.valueOf(LocalDate.now(ZONE).minusDays(5).atStartOfDay())}));

        BookResponse response = service.getPublicBookById(10L);

        assertThat(response.availableCount()).isEqualTo(0L);
        assertThat(response.queueCount()).isEqualTo(0L);
        assertThat(response.earliestExpectedReturnDate()).isEqualTo(returnDate);
        assertThat(response.expectedReturnNotice()).contains(String.format("%02d/%02d/%d", returnDate.getDayOfMonth(), returnDate.getMonthValue(), returnDate.getYear()));
    }

    @Test
    @DisplayName("Test 2: Không còn bản rảnh và có 1 người đặt giữ (queueCount = 1)")
    void testNoAvailableCopiesAndSingleReservation() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(10L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(10L)).thenReturn(1L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(10L)).thenReturn(List.of());
        when(bookCopyRepository.countAvailableByBookId(10L)).thenReturn(0L);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(1L);
        LocalDate returnDate = LocalDate.now(ZONE).plusDays(3);
        Timestamp dueTs = Timestamp.valueOf(returnDate.atStartOfDay());
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{dueTs, Timestamp.valueOf(LocalDate.now(ZONE).minusDays(7).atStartOfDay())}));

        BookResponse response = service.getPublicBookById(10L);

        assertThat(response.availableCount()).isEqualTo(0L);
        assertThat(response.queueCount()).isEqualTo(1L);
        assertThat(response.earliestExpectedReturnDate()).isEqualTo(returnDate);
    }

    @Test
    @DisplayName("Test 3: Có nhiều người đặt giữ (queueCount = 5)")
    void testMultipleReservationsInQueue() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(10L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(10L)).thenReturn(2L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(10L)).thenReturn(List.of());
        when(bookCopyRepository.countAvailableByBookId(10L)).thenReturn(0L);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(5L);
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L)).thenReturn(List.of());

        BookResponse response = service.getPublicBookById(10L);

        assertThat(response.availableCount()).isEqualTo(0L);
        assertThat(response.queueCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Test 4: Có nhiều bản đang mượn với ngày trả khác nhau -> Chọn ngày trả sớm nhất")
    void testMultipleLoansDifferentDueDatesSelectsEarliest() {
        LocalDate earlier = LocalDate.now(ZONE).plusDays(2);
        LocalDate later = LocalDate.now(ZONE).plusDays(10);
        Timestamp dueTs1 = Timestamp.valueOf(later.atStartOfDay());
        Timestamp dueTs2 = Timestamp.valueOf(earlier.atStartOfDay());

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(2L);
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(
                        new Object[]{dueTs1, Timestamp.valueOf(LocalDate.now(ZONE).minusDays(10).atStartOfDay())},
                        new Object[]{dueTs2, Timestamp.valueOf(LocalDate.now(ZONE).minusDays(5).atStartOfDay())}
                ));

        BookQueueInfoResponse queueInfo = service.loadQueueAndExpectedReturnInfo(10L);

        assertThat(queueInfo.earliestExpectedReturnDate()).isEqualTo(earlier);
        assertThat(queueInfo.queueCount()).isEqualTo(2L);
        assertThat(queueInfo.hasOverdueCopies()).isFalse();
    }

    @Test
    @DisplayName("Test 5: Có bản quá hạn (quá hạn 3 ngày và 1 bản hợp lệ)")
    void testLoanWithOverdueAndFutureDueDate() {
        LocalDate overdue = LocalDate.now(ZONE).minusDays(3);
        LocalDate future = LocalDate.now(ZONE).plusDays(4);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(1L);
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(
                        new Object[]{Timestamp.valueOf(overdue.atStartOfDay()), Timestamp.valueOf(LocalDate.now(ZONE).minusDays(20).atStartOfDay())},
                        new Object[]{Timestamp.valueOf(future.atStartOfDay()), Timestamp.valueOf(LocalDate.now(ZONE).minusDays(5).atStartOfDay())}
                ));

        BookQueueInfoResponse queueInfo = service.loadQueueAndExpectedReturnInfo(10L);

        assertThat(queueInfo.hasOverdueCopies()).isTrue();
        assertThat(queueInfo.earliestExpectedReturnDate()).isEqualTo(future);
        assertThat(queueInfo.expectedReturnNotice()).contains("quá hạn");
    }

    @Test
    @DisplayName("Test 5b: Tất cả các bản đang mượn đều quá hạn -> earliestExpectedReturnDate null")
    void testAllLoansOverdue() {
        LocalDate overdue1 = LocalDate.now(ZONE).minusDays(5);
        LocalDate overdue2 = LocalDate.now(ZONE).minusDays(1);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(0L);
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(
                        new Object[]{Timestamp.valueOf(overdue1.atStartOfDay()), Timestamp.valueOf(LocalDate.now(ZONE).minusDays(25).atStartOfDay())},
                        new Object[]{Timestamp.valueOf(overdue2.atStartOfDay()), Timestamp.valueOf(LocalDate.now(ZONE).minusDays(20).atStartOfDay())}
                ));

        BookQueueInfoResponse queueInfo = service.loadQueueAndExpectedReturnInfo(10L);

        assertThat(queueInfo.earliestExpectedReturnDate()).isNull();
        assertThat(queueInfo.hasOverdueCopies()).isTrue();
        assertThat(queueInfo.expectedReturnNotice()).contains("Tất cả các bản mượn hiện đã quá hạn");
    }

    @Test
    @DisplayName("Test 6: Không xác định được ngày trả (không có bản nào đang mượn hoặc không có hạn)")
    void testCannotDetermineDueDate() {
        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(0L);
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L)).thenReturn(List.of());

        BookQueueInfoResponse queueInfo = service.loadQueueAndExpectedReturnInfo(10L);

        assertThat(queueInfo.earliestExpectedReturnDate()).isNull();
        assertThat(queueInfo.expectedReturnNotice()).containsIgnoringCase("chưa xác định được ngày dự kiến trả");
    }

    @Test
    @DisplayName("Test 7: Xuất hiện bản Sẵn sàng trở lại -> Ẩn thông tin hàng đợi và ngày trả")
    void testAvailableCopyReturnsHidesQueueInfo() {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(10L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(10L)).thenReturn(2L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{101L, "LIB-101", 1L, "KHO-A", "Kho A", 1L, "A01", "Kệ 1"}));

        BookResponse response = service.getPublicBookById(10L);

        assertThat(response.availableCount()).isEqualTo(1L);
        assertThat(response.availableCopies()).hasSize(1);
        assertThat(response.queueCount()).isNull();
        assertThat(response.earliestExpectedReturnDate()).isNull();
        assertThat(response.expectedReturnNotice()).isNull();
    }

    @Test
    @DisplayName("Test 8: Kiểm tra không xuất hiện tên người mượn hoặc người đặt giữ trong public API")
    void testNoBorrowerOrReaderNamesExposed() throws Exception {
        when(bookRepository.findPublicByIdWithAuthorAndCategory(10L)).thenReturn(Optional.of(sampleBook));
        when(bookCopyRepository.countByBookId(10L)).thenReturn(1L);
        when(bookCopyRepository.findAvailableCopyLocationsByBookId(10L)).thenReturn(List.of());
        when(bookCopyRepository.countAvailableByBookId(10L)).thenReturn(0L);

        when(bookReservationRepository.countPendingQueueByBookId(10L)).thenReturn(3L);
        LocalDate returnDate = LocalDate.now(ZONE).plusDays(6);
        Timestamp dueTs = Timestamp.valueOf(returnDate.atStartOfDay());
        when(bookReservationRepository.findUnreturnedLoanDatesByBookId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{dueTs, Timestamp.valueOf(LocalDate.now(ZONE).minusDays(4).atStartOfDay())}));

        mockMvc.perform(get("/api/v1/books/public/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.availableCount").value(0))
                .andExpect(jsonPath("$.queueCount").value(3))
                .andExpect(jsonPath("$.borrowerName").doesNotExist())
                .andExpect(jsonPath("$.borrowerEmail").doesNotExist())
                .andExpect(jsonPath("$.readerName").doesNotExist())
                .andExpect(jsonPath("$.readerEmail").doesNotExist())
                .andExpect(jsonPath("$.borrowerUserId").doesNotExist());
    }
}
