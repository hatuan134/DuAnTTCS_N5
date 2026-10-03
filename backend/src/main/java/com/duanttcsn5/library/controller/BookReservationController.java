package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.dto.book.BookReservationQueueResponse;
import com.duanttcsn5.library.dto.book.ReadyForPickupReservationResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BookReservationController {
    private final BookReservationService reservations;

    public BookReservationController(BookReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/books/{bookId}/reservations")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<BookReservationResponse> reserve(
            @PathVariable Long bookId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservations.reserve(bookId, principal == null ? null : principal.id()));
    }

    @GetMapping("/books/{bookId}/reservations")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<BookReservationQueueResponse> getQueueByBookId(@PathVariable Long bookId) {
        return ResponseEntity.ok(reservations.getQueueByBookId(bookId));
    }

    @GetMapping("/reservations/ready-for-pickup")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ReadyForPickupReservationResponse>> getReadyForPickup() {
        return ResponseEntity.ok(reservations.getReadyForPickup());
    }

    @GetMapping("/reservations/ready-for-pickup/{reservationId}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReadyForPickupReservationResponse> getReadyForPickupById(
            @PathVariable Long reservationId) {
        return ResponseEntity.ok(reservations.getReadyForPickupById(reservationId));
    }
}
