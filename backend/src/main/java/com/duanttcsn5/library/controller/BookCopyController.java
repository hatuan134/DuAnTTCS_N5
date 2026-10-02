package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.dto.bookcopy.BookCopySummaryResponse;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
import com.duanttcsn5.library.dto.bookcopy.UpdateBookCopyRequest;
import com.duanttcsn5.library.service.BookCopyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
public class BookCopyController {

    private final BookCopyService service;

    public BookCopyController(BookCopyService service) {
        this.service = service;
    }

    @PostMapping("/books/{bookId}/copies")
    public ResponseEntity<BookCopyResponse> create(
            @PathVariable Long bookId,
            @Valid @RequestBody CreateBookCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(bookId, request));
    }

    @GetMapping("/books/{bookId}/copies")
    public ResponseEntity<List<BookCopyResponse>> getByBookId(@PathVariable Long bookId) {
        return ResponseEntity.ok(service.getByBookId(bookId));
    }

    @GetMapping("/books/{bookId}/copies/summary")
    public ResponseEntity<BookCopySummaryResponse> getSummaryByBookId(@PathVariable Long bookId) {
        return ResponseEntity.ok(service.getSummaryByBookId(bookId));
    }

    @GetMapping("/book-copies/{id}")
    public ResponseEntity<BookCopyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @RequestMapping(value = "/book-copies/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<BookCopyResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateBookCopyRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }
}
