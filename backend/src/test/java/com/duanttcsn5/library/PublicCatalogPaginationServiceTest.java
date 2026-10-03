package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.PublicCatalogPageResponse;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicCatalogPaginationServiceTest {
    @Mock BookRepository books;
    @Mock BookCopyRepository copies;
    @Mock AuthorRepository authors;
    @Mock CategoryRepository categories;
    @Mock AuditLogRepository audit;
    private BookCatalogService service;

    @BeforeEach void setup() {
        service = new BookCatalogService(books, copies, authors, categories, audit);
    }

    @Test void fewerThanTwentyAndExactlyTwentyHaveOnePage() {
        for (int count : new int[]{1, 19, 20}) {
            data(numberedBooks(count));
            var result = search(null, 0, "relevance");
            assertEquals(count, result.content().size());
            assertEquals(count, result.totalElements());
            assertEquals(1, result.totalPages());
            assertEquals(20, result.size());
            assertTrue(result.first());
            assertTrue(result.last());
        }
    }

    @Test void twentyOneResultsHaveTwoPages() {
        data(numberedBooks(21));
        var first = search(null, 0, "relevance");
        var last = search(null, 1, "relevance");
        assertEquals(20, first.content().size());
        assertEquals(1, last.content().size());
        assertEquals(2, first.totalPages());
        assertFalse(first.last());
        assertFalse(last.first());
        assertTrue(last.last());
    }

    @Test void manyPagesHaveNoDuplicatesOrMissingBooksAndPreviousPageIsStable() {
        List<Book> input = numberedBooks(61);
        Collections.reverse(input);
        data(input);
        for (String sort : List.of("relevance", "publicationYear")) {
            List<Long> all = new ArrayList<>();
            for (int page = 0; page < 4; page++) {
                var result = search(null, page, sort);
                assertEquals(page, result.page());
                assertEquals(4, result.totalPages());
                assertTrue(result.content().size() <= 20);
                all.addAll(ids(result));
            }
            assertEquals(61, all.stream().distinct().count());
            assertEquals(IntStream.rangeClosed(1, 61).mapToObj(i -> (long) i).toList(),
                    all.stream().sorted().toList());
            var previous = search(null, 1, sort);
            search(null, 2, sort);
            Collections.rotate(input, 17);
            when(books.findAllPublicWithAuthorAndCategory()).thenReturn(input);
            assertEquals(ids(previous), ids(search(null, 1, sort)));
        }
    }

    @Test void combinedFiltersStayAppliedAcrossPagesAndChangingFilterStartsAtZero() {
        List<Book> input = numberedBooks(55);
        input.forEach(book -> book.setPublicationYear(book.getId() <= 45 ? 2008 : 1990));
        data(input);
        when(copies.countAvailableGroupedByBookId()).thenReturn(
                input.stream().filter(book -> book.getId() <= 41)
                        .map(book -> new Object[]{book.getId(), 1L}).toList());
        when(categories.existsById(6L)).thenReturn(true);
        var second = service.searchPublicBooks("sach", 6L, 2008, true, 1, 20, "publicationYear");
        assertEquals(41, second.totalElements());
        assertEquals(3, second.totalPages());
        assertEquals("publicationYear", second.sort());
        assertTrue(second.content().stream().allMatch(book -> book.publicationYear() == 2008
                && book.categoryId() == 6L && book.availableCount() > 0));
        var changed = service.searchPublicBooks("sach", 6L, 1990, false, 0, 20, "publicationYear");
        assertEquals(0, changed.page());
        assertEquals(10, changed.totalElements());
    }

    @Test void exactTitleAndCombinedCriteriaOutrankPartialTitle() {
        Book partial = book(1, "Mắt biếc bản đặc biệt", "Khác", 2000);
        Book exact = book(2, "Mắt biếc", "Khác", 2000);
        Book combined = book(3, "Mắt biếc", "Mắt biếc", 2000);
        data(List.of(partial, exact, combined));
        assertEquals(List.of(3L, 2L, 1L), ids(search("  MAT BIEC  ", 0, "relevance")));
    }

    @Test void exactAuthorOutranksPartialAuthorAndCountsAuthorCriterionOnce() {
        Book partial = book(1, "A", "Nguyễn Nhật Ánh và cộng sự", 2000);
        Book exact = book(2, "B", "Nguyễn Nhật Ánh", 2000);
        Book exactWithCoauthor = book(3, "C", "Nguyễn Nhật Ánh", 2000);
        Author coauthor = new Author("Nguyễn Nhật Ánh", null, true);
        coauthor.setId(99L);
        exactWithCoauthor.setAuthors(List.of(exactWithCoauthor.getAuthor(), coauthor));
        data(List.of(partial, exactWithCoauthor, exact));
        assertEquals(List.of(2L, 3L, 1L), ids(search("NGUYEN NHAT ANH", 0, "relevance")));
    }

    @Test void exactIsbnOutranksPartialIsbnIgnoringSeparators() {
        Book partial = book(1, "A", "Khác", 2000);
        partial.setIsbn("978604200001199");
        Book exact = book(2, "B", "Khác", 2000);
        exact.setIsbn("978-604-2-00001-1");
        data(List.of(partial, exact));
        assertEquals(List.of(2L, 1L), ids(search("978 604 2 00001 1", 0, "relevance")));
    }

    @Test void publicationYearSortIsDescendingWithNullLastAndStableIdTies() {
        data(List.of(book(5, "C", "Khác", null), book(4, "B", "Khác", 2008),
                book(3, "A", "Khác", 2008), book(2, "A", "Khác", 2008),
                book(1, "D", "Khác", 1990)));
        assertEquals(List.of(2L, 3L, 4L, 1L, 5L), ids(search(null, 0, "publicationYear")));
    }

    @Test void tieByNormalizedTitleThenIdIsStableForRelevanceToo() {
        data(List.of(book(3, "MẮT BIẾC", "Khác", 2008),
                book(2, "Mắt biếc", "Khác", 2008), book(1, "Mắt biếc", "Khác", 2008)));
        assertEquals(List.of(1L, 2L, 3L), ids(search("mat biec", 0, "relevance")));
    }

    @Test void noCopiesRemainHiddenBeforePaginationAndCount() {
        data(numberedBooks(21));
        when(copies.countAllGroupedByBookId()).thenReturn(
                IntStream.rangeClosed(1, 20).mapToObj(i -> new Object[]{(long) i, 1L}).toList());
        var result = search(null, 0, "relevance");
        assertEquals(20, result.totalElements());
        assertEquals(1, result.totalPages());
        assertFalse(ids(result).contains(21L));
    }

    @Test void outOfRangePageClampsToLastAndEmptyResultIsZeroOfZero() {
        data(numberedBooks(21));
        assertEquals(1, search(null, Integer.MAX_VALUE, "relevance").page());
        var empty = search("khong ton tai", 99, "relevance");
        assertTrue(empty.content().isEmpty());
        assertEquals(0, empty.page());
        assertEquals(0, empty.totalPages());
        assertTrue(empty.first());
        assertTrue(empty.last());
    }

    @Test void invalidPaginationAndSortAreRejectedBeforeQueries() {
        assertError("INVALID_PAGE", -1, 20, "relevance");
        assertError("INVALID_PAGE_SIZE", 0, 0, "relevance");
        assertError("INVALID_PAGE_SIZE", 0, -1, "relevance");
        assertError("INVALID_PAGE_SIZE", 0, 21, "relevance");
        assertError("INVALID_CATALOG_SORT", 0, 20, "unknown");
        assertError("INVALID_CATALOG_SORT", 0, 20, null);
        verifyNoInteractions(books, copies, categories);
    }

    @Test void smallerPageSizeStillHonorsMaximumAndMetadata() {
        data(numberedBooks(21));
        var result = service.searchPublicBooks(null, null, null, false, 4, 5, "relevance");
        assertEquals(5, result.size());
        assertEquals(5, result.totalPages());
        assertEquals(1, result.content().size());
        assertTrue(result.last());
    }

    private void assertError(String code, int page, int size, String sort) {
        ApiException error = assertThrows(ApiException.class,
                () -> service.searchPublicBooks(null, null, null, false, page, size, sort));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(code, error.getCode());
    }

    private PublicCatalogPageResponse search(String keyword, int page, String sort) {
        return service.searchPublicBooks(keyword, null, null, false, page, 20, sort);
    }

    private List<Long> ids(PublicCatalogPageResponse result) {
        return result.content().stream().map(BookResponse::id).toList();
    }

    private void data(List<Book> input) {
        when(books.findAllPublicWithAuthorAndCategory()).thenReturn(input);
        when(copies.countAllGroupedByBookId()).thenReturn(
                input.stream().map(book -> new Object[]{book.getId(), 1L}).toList());
        when(copies.countAvailableGroupedByBookId()).thenReturn(List.of());
    }

    private List<Book> numberedBooks(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count)
                .mapToObj(i -> book(i, String.format("Sách %03d", i), "Tác giả", 2008)).toList());
    }

    private Book book(long id, String title, String authorName, Integer year) {
        Author author = new Author(authorName, null, true);
        author.setId(id);
        Category category = new Category("Văn học", null, null, true);
        category.setId(6L);
        Book book = new Book(null, title, author, category, "NXB Trẻ", year, null);
        book.setId(id);
        return book;
    }
}
