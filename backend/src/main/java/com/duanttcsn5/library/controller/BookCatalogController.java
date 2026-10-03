package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.BookAvailableCopyLocationResponse;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.dto.book.PublicCatalogFilterOptionsResponse;
import com.duanttcsn5.library.dto.book.PublicCatalogPageResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
public class BookCatalogController {

    private final BookCatalogService bookCatalogService;

    public BookCatalogController(BookCatalogService bookCatalogService) {
        this.bookCatalogService = bookCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<BookResponse>> getAllBooks() {
        return ResponseEntity.ok(bookCatalogService.getAllBooks());
    }

    @GetMapping("/public")
    public ResponseEntity<List<BookResponse>> getPublicBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer publicationYear,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        return ResponseEntity.ok(bookCatalogService.getPublicBooks(
                keyword, categoryId, publicationYear, availableOnly));
    }

    @GetMapping("/public/search")
    public ResponseEntity<PublicCatalogPageResponse> searchPublicBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer publicationYear,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevance") String sort) {
        return ResponseEntity.ok(bookCatalogService.searchPublicBooks(
                keyword, categoryId, publicationYear, availableOnly, page, size, sort));
    }

    @GetMapping("/public/filters")
    public ResponseEntity<PublicCatalogFilterOptionsResponse> getPublicFilterOptions() {
        return ResponseEntity.ok(bookCatalogService.getPublicFilterOptions());
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<BookResponse> getPublicBookById(@PathVariable Long id) {
        return ResponseEntity.ok(bookCatalogService.getPublicBookById(id));
    }

    @GetMapping("/public/{id}/available-copies")
    public ResponseEntity<List<BookAvailableCopyLocationResponse>> getAvailableCopiesByBookId(@PathVariable Long id) {
        return ResponseEntity.ok(bookCatalogService.getAvailableCopiesByBookId(id));
    }

    @GetMapping("/publishers")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<String>> getPublisherOptions() {
        return ResponseEntity.ok(bookCatalogService.getPublisherOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<BookResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookCatalogService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<BookResponse> catalogBook(
            @Valid @RequestBody CatalogBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        BookResponse response = bookCatalogService.catalogBook(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
