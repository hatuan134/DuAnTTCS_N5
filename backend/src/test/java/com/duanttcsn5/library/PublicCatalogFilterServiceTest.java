package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Year;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicCatalogFilterServiceTest {
    @Mock BookRepository books;
    @Mock BookCopyRepository copies;
    @Mock AuthorRepository authors;
    @Mock CategoryRepository categories;
    @Mock AuditLogRepository audit;
    private BookCatalogService service;

    @BeforeEach
    void setup() {
        service = new BookCatalogService(books, copies, authors, categories, audit);
        Category literature = category(6L, "Văn học trong nước");
        Category technology = category(2L, "Công nghệ thông tin");
        // Book 105 không có bản sao; 104 có năm xuất bản chưa xác định.
        lenient().when(books.findAllPublicWithAuthorAndCategory()).thenReturn(List.of(
                book(101L, "Mắt biếc", literature, 2008),
                book(102L, "Mắt biếc bản cũ", literature, 1990),
                book(103L, "Lập trình Java", technology, 2008),
                book(104L, "Sách văn học khác", literature, null),
                book(105L, "Sách chưa có bản sao", literature, 2008)));
        lenient().when(copies.countAllGroupedByBookId()).thenReturn(List.of(
                new Object[]{101L, 7L}, new Object[]{102L, 4L},
                new Object[]{103L, 1L}, new Object[]{104L, 1L}));
        // Truy vấn repository chỉ đếm AVAILABLE, không tính các trạng thái khác.
        lenient().when(copies.countAvailableGroupedByBookId()).thenReturn(List.of(
                new Object[]{101L, 2L}, new Object[]{103L, 1L}));
        lenient().when(categories.existsById(6L)).thenReturn(true);
        lenient().when(categories.existsById(2L)).thenReturn(true);
    }

    @Test void categoryAlone() {
        assertIds(List.of(101L, 102L, 104L), service.getPublicBooks(null, 6L, null, false));
    }

    @Test void yearAloneExcludesUnknownYear() {
        assertIds(List.of(101L, 103L), service.getPublicBooks(null, null, 2008, false));
    }

    @Test void availableOnlyExcludesBooksWithoutAvailableCopies() {
        List<BookResponse> result = service.getPublicBooks(null, null, null, true);
        assertIds(List.of(101L, 103L), result);
        assertEquals(2L, result.get(0).availableCount());
        assertTrue(result.stream().allMatch(book -> book.availableCount() > 0));
    }

    @Test void keywordAndCategoryUseIntersection() {
        assertIds(List.of(101L, 102L), service.getPublicBooks("MAT BIEC", 6L, null, false));
        assertIds(List.of(), service.getPublicBooks("MAT BIEC", 2L, null, false));
    }

    @Test void keywordAndYearUseIntersection() {
        assertIds(List.of(102L), service.getPublicBooks("mat biec", null, 1990, false));
    }

    @Test void keywordAndAvailabilityUseIntersection() {
        assertIds(List.of(101L), service.getPublicBooks("mat biec", null, null, true));
    }

    @Test void allFiltersApplyTogether() {
        assertIds(List.of(101L), service.getPublicBooks("NGUYEN NHAT ANH", 6L, 2008, true));
        assertIds(List.of(), service.getPublicBooks("mat biec", 6L, 1990, true));
    }

    @Test void removingFiltersBroadensResultsAndPreservesKeyword() {
        assertIds(List.of(101L), service.getPublicBooks("mat biec", 6L, 2008, true));
        assertIds(List.of(101L), service.getPublicBooks("mat biec", 6L, null, true));
        assertIds(List.of(101L, 102L), service.getPublicBooks("mat biec", 6L, null, false));
        assertIds(List.of(101L, 102L), service.getPublicBooks("mat biec", null, null, false));
    }

    @Test void legacyOverloadsReturnAllPublicBooks() {
        assertIds(List.of(101L, 102L, 103L, 104L), service.getPublicBooks());
        assertIds(List.of(101L, 102L), service.getPublicBooks("mat biec"));
    }

    @Test void availabilityIsRecomputedOnEverySearch() {
        assertIds(List.of(101L), service.getPublicBooks("mat biec", null, null, true));
        when(copies.countAvailableGroupedByBookId()).thenReturn(List.of());
        assertIds(List.of(), service.getPublicBooks("mat biec", null, null, true));
    }

    @Test void validatesCategoryBeforeSearching() {
        for (long id : new long[]{0L, -1L, 999L}) {
            ApiException error = assertThrows(ApiException.class,
                    () -> service.getPublicBooks(null, id, null, false));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
            assertEquals(id == 999L ? "CATEGORY_NOT_FOUND" : "INVALID_CATEGORY_FILTER", error.getCode());
        }
        verify(books, never()).findAllPublicWithAuthorAndCategory();
    }

    @Test void validatesYearBeforeSearching() {
        int futureYear = Year.now(ZoneId.of("Asia/Ho_Chi_Minh")).getValue() + 1;
        for (int year : new int[]{0, -1, futureYear}) {
            ApiException error = assertThrows(ApiException.class,
                    () -> service.getPublicBooks(null, null, year, false));
            assertEquals("INVALID_PUBLICATION_YEAR", error.getCode());
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        }
        verify(books, never()).findAllPublicWithAuthorAndCategory();
    }

    @Test void optionsAreUniqueAndIncludeInactiveCategoriesWithPublicBooks() {
        Category inactive = category(9L, "Thể loại cũ");
        inactive.setActive(false);
        when(books.findPublicFilterRows()).thenReturn(List.of(
                new Object[]{9L, "Thể loại cũ", 2008}, new Object[]{9L, "Thể loại cũ", 2008},
                new Object[]{6L, "Văn học", 1990}, new Object[]{9L, "Thể loại cũ", null}));
        var options = service.getPublicFilterOptions();
        assertEquals(List.of(9L, 6L), options.categories().stream().map(c -> c.id()).toList());
        assertEquals(List.of(2008, 1990), options.publicationYears());
        verifyNoInteractions(copies, categories);
    }

    @Test void emptyCatalogHasEmptyOptions() {
        when(books.findPublicFilterRows()).thenReturn(List.of());
        var options = service.getPublicFilterOptions();
        assertTrue(options.categories().isEmpty());
        assertTrue(options.publicationYears().isEmpty());
    }

    private void assertIds(List<Long> expected, List<BookResponse> actual) {
        assertEquals(expected, actual.stream().map(BookResponse::id).toList());
    }

    private Category category(Long id, String name) {
        Category category = new Category(name, null, null, true);
        category.setId(id);
        return category;
    }

    private Book book(Long id, String title, Category category, Integer year) {
        Author author = new Author("Nguyễn Nhật Ánh", null, true);
        author.setId(1L);
        Book book = new Book("9786040000001", title, author, category, "NXB Trẻ", year, "Mô tả");
        book.setId(id);
        return book;
    }
}
