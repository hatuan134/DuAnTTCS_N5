package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.author.AuthorResponse;
import com.duanttcsn5.library.dto.author.CreateAuthorRequest;
import com.duanttcsn5.library.dto.author.UpdateAuthorRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.AuthorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping
    public ResponseEntity<List<AuthorResponse>> getAllAuthors() {
        return ResponseEntity.ok(authorService.getAllAuthors());
    }

    @GetMapping("/active")
    public ResponseEntity<List<AuthorResponse>> getActiveAuthors() {
        return ResponseEntity.ok(authorService.getActiveAuthors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorResponse> getAuthorById(@PathVariable Long id) {
        return ResponseEntity.ok(authorService.getAuthorById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuthorResponse> createAuthor(
            @Valid @RequestBody CreateAuthorRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.createAuthor(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuthorResponse> updateAuthor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAuthorRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.updateAuthor(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AuthorResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        AuthorResponse response = authorService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteAuthor(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        authorService.deleteAuthor(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
