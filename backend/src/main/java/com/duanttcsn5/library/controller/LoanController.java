package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.CreateReservationLoanRequest;
import com.duanttcsn5.library.dto.loan.ReservationLoanContextResponse;
import com.duanttcsn5.library.dto.loan.ReservationLoanResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LoanService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
public class LoanController {
    private final LoanService loans;

    public LoanController(LoanService loans) { this.loans = loans; }

    @GetMapping("/{reservationId}/loan-context")
    public ResponseEntity<ReservationLoanContextResponse> pickupContext(@PathVariable Long reservationId) {
        return ResponseEntity.ok(loans.pickupContext(reservationId));
    }

    @PostMapping("/{reservationId}/pickup-check")
    public ResponseEntity<ReservationLoanContextResponse> checkPickup(@PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.checkPickup(reservationId, principal == null ? null : principal.id()));
    }

    @PostMapping("/{reservationId}/loan")
    public ResponseEntity<ReservationLoanResponse> createFromReservation(
            @PathVariable Long reservationId, @Valid @RequestBody CreateReservationLoanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loans.createFromReservation(
                reservationId, principal == null ? null : principal.id(), request.cardNumber(),
                request.expectedBorrowDate(), request.expectedDueAt(), request.expectedLoanDays()));
    }
}
