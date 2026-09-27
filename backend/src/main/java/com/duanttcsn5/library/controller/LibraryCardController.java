package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.librarycard.ApproveLibraryCardRequest;
import com.duanttcsn5.library.dto.librarycard.LibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.MyLibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.PendingReaderApplicationResponse;
import com.duanttcsn5.library.dto.librarycard.RejectReaderApplicationRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LibraryCardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/library-cards")
public class LibraryCardController {

    private final LibraryCardService libraryCardService;

    public LibraryCardController(LibraryCardService libraryCardService) {
        this.libraryCardService = libraryCardService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<List<PendingReaderApplicationResponse>> getPendingApplications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(libraryCardService.getPendingApplications(search, fromDate, toDate));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<List<LibraryCardResponse>> getIssuedCards() {
        return ResponseEntity.ok(libraryCardService.getIssuedCards());
    }

    @PostMapping("/{readerUserId}/approve")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<LibraryCardResponse> approve(
            @PathVariable Long readerUserId,
            @Valid @RequestBody ApproveLibraryCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        LibraryCardResponse response = libraryCardService.approve(
                readerUserId,
                request,
                principal.id(),
                servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{readerUserId}/reject")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<Void> reject(
            @PathVariable Long readerUserId,
            @Valid @RequestBody RejectReaderApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        libraryCardService.reject(
                readerUserId,
                request,
                principal.id(),
                servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<MyLibraryCardResponse> getMyCard(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(libraryCardService.getMyCard(principal.id()));
    }
}
