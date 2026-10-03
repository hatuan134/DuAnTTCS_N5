package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books/{bookId}/reservations")
public class BookReservationController {
    private final BookReservationService reservations;

    public BookReservationController(BookReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<BookReservationResponse> reserve(
            @PathVariable Long bookId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservations.reserve(bookId, principal == null ? null : principal.id()));
    }
}
