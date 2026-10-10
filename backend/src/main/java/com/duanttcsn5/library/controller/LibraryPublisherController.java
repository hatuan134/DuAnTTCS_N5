package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.CreatePublisherRequest;
import com.duanttcsn5.library.dto.book.PublisherResponse;
import com.duanttcsn5.library.service.LibraryPublisherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publishers")
public class LibraryPublisherController {
    private final LibraryPublisherService publisherService;

    public LibraryPublisherController(LibraryPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<PublisherResponse> create(@Valid @RequestBody CreatePublisherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publisherService.create(request.name()));
    }
}
