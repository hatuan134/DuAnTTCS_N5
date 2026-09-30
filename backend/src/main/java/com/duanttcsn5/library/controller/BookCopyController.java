package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
import com.duanttcsn5.library.service.BookCopyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
public class BookCopyController {
    private final BookCopyService service;
    public BookCopyController(BookCopyService service) { this.service = service; }

    @PostMapping("/books/{bookId}/copies")
    public ResponseEntity<BookCopyResponse> create(@PathVariable Long bookId,
            @Valid @RequestBody CreateBookCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(bookId, request));
    }

    @GetMapping("/book-copies/{id}")
    public ResponseEntity<BookCopyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // Explicit rejection makes an attempted reassignment reviewable, without implementing an editor.
    @RequestMapping(value = "/book-copies/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Void> rejectUpdate(@PathVariable Long id) {
        service.rejectUpdate(id);
        return ResponseEntity.noContent().build();
    }
}
