package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<List<BookResponse>> getAllBooks() {
        return ResponseEntity.ok(bookCatalogService.getAllBooks());
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
