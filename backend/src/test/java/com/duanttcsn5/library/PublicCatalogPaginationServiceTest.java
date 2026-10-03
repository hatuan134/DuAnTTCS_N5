package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.BookCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicCatalogPaginationServiceTest {
    @Mock BookRepository books;
    @Mock BookCopyRepository copies;
    @Mock AuthorRepository authors;
    @Mock CategoryRepository categories;
    @Mock AuditLogRepository audit;
    BookCatalogService service;
    @BeforeEach void setup() { service = new BookCatalogService(books, copies, authors, categories, audit); }

    @Test void loadsOnlyPageAndRestoresSqlOrderWithoutWholeCatalogQueries() {
        when(books.countPublicSearch("mat biec", "", null, null, false)).thenReturn(5000L);
        when(books.findPublicSearchIds("mat biec", "", null, null, false, "relevance", 20, 20L))
                .thenReturn(List.of(3L, 2L));
        when(books.findPublicPageWithAuthorAndCategory(List.of(3L, 2L)))
                .thenReturn(List.of(book(2L), book(3L)));
        when(copies.countCopiesForPublicPage(List.of(3L, 2L))).thenReturn(List.of(
                new Object[]{2L, 3L, 1L}, new Object[]{3L, 7L, 0L}));
        var page = service.searchPublicBooks("  MẮT BIẾC  ", null, null, false, 1, 20, "relevance");
        assertEquals(List.of(3L, 2L), page.content().stream().map(b -> b.id()).toList());
        assertEquals(7, page.content().get(0).copyCount());
        assertEquals(0, page.content().get(0).availableCount());
        assertEquals(1, page.content().get(1).availableCount());
        assertEquals(5000, page.totalElements());
        assertEquals(250, page.totalPages());
        assertFalse(page.first()); assertFalse(page.last());
        verify(books, never()).findAllPublicWithAuthorAndCategory();
        verify(copies, never()).countAllGroupedByBookId();
        verify(copies, never()).countAvailableGroupedByBookId();
    }
    @Test void countAndPageReceiveIdenticalCombinedFiltersAndNormalizedIsbn() {
        when(categories.existsById(6L)).thenReturn(true);
        when(books.countPublicSearch("978 604-123", "978604123", 6L, 2008, true)).thenReturn(1L);
        when(books.findPublicSearchIds("978 604-123", "978604123", 6L, 2008, true, "publicationYear", 5, 0L))
                .thenReturn(List.of(1L));
        when(books.findPublicPageWithAuthorAndCategory(List.of(1L))).thenReturn(List.of(book(1L)));
        when(copies.countCopiesForPublicPage(List.of(1L))).thenReturn(java.util.Collections.singletonList(new Object[]{1L, 1L, 1L}));
        var page = service.searchPublicBooks("978 604-123", 6L, 2008, true, 0, 5, "publicationYear");
        assertEquals("publicationYear", page.sort()); assertEquals(5, page.size()); assertTrue(page.last());
    }
    @Test void emptyResultStopsBeforeFetchingEntitiesOrCopyCounts() {
        var page = service.searchPublicBooks("none", null, null, false, 99, 20, "relevance");
        assertTrue(page.content().isEmpty()); assertEquals(0, page.page()); assertEquals(0, page.totalPages());
        assertTrue(page.first()); assertTrue(page.last());
        verify(books, never()).findPublicPageWithAuthorAndCategory(anyList()); verifyNoInteractions(copies);
    }
    @Test void excessivePageClampsToLastPageBeforeComputingOffset() {
        when(books.countPublicSearch("", "", null, null, false)).thenReturn(21L);
        when(books.findPublicSearchIds("", "", null, null, false, "relevance", 20, 20L)).thenReturn(List.of(1L));
        when(books.findPublicPageWithAuthorAndCategory(List.of(1L))).thenReturn(List.of(book(1L)));
        var page = service.searchPublicBooks(null, null, null, false, Integer.MAX_VALUE, 20, "relevance");
        assertEquals(1, page.page()); assertEquals(2, page.totalPages()); assertTrue(page.last());
    }
    @Test void invalidPaginationSortAndFiltersStopBeforeDatabaseSearch() {
        for (int size : new int[]{-1, 0, 21}) assertThrows(ApiException.class,
                () -> service.searchPublicBooks(null, null, null, false, 0, size, "relevance"));
        assertThrows(ApiException.class, () -> service.searchPublicBooks(null, null, null, false, -1, 20, "relevance"));
        for (String sort : new String[]{null, "unknown"}) assertThrows(ApiException.class,
                () -> service.searchPublicBooks(null, null, null, false, 0, 20, sort));
        assertThrows(ApiException.class, () -> service.searchPublicBooks(null, 0L, null, false, 0, 20, "relevance"));
        assertThrows(ApiException.class, () -> service.searchPublicBooks(null, null, 0, false, 0, 20, "relevance"));
        verifyNoInteractions(books, copies, categories);
    }
    private Book book(long id) {
        Author author = new Author("Nguyễn Nhật Ánh", null, true); author.setId(1L);
        Category category = new Category("Văn học", null, null, true); category.setId(6L);
        Book book = new Book(null, "Mắt biếc", author, category, "NXB Trẻ", 2008, null); book.setId(id);
        return book;
    }
}
