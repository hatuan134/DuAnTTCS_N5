package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.ReaderLoanEligibilityResponse;
import com.duanttcsn5.library.dto.loan.CheckReaderLoanRequest;
import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.dto.loan.LoanRejectionPageResponse;
import com.duanttcsn5.library.service.LoanRejectionLogService;
import com.duanttcsn5.library.dto.loan.AddDirectLoanItemRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanItemResponse;
import com.duanttcsn5.library.dto.loan.CreateDirectLoanRequest;
import com.duanttcsn5.library.dto.loan.DirectLoanResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse;
import com.duanttcsn5.library.dto.loan.MyReturnedBooksPageResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import java.util.List;
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
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
public class LoanController {
    private final LoanService loans;
    private final LoanRejectionLogService rejections;

    @org.springframework.beans.factory.annotation.Autowired
    public LoanController(LoanService loans,
                          org.springframework.beans.factory.ObjectProvider<LoanRejectionLogService> provider) {
        this.loans = loans;
        this.rejections = provider.getIfAvailable();
    }

    /** Preserve the current constructor for isolated controller unit tests. */
    public LoanController(LoanService loans) {
        this.loans = loans;
        this.rejections = null;
    }

    @GetMapping("/reservations/{reservationId}/loan-context")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReservationLoanContextResponse> pickupContext(@PathVariable Long reservationId) {
        return ResponseEntity.ok(loans.pickupContext(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/pickup-check")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReservationLoanContextResponse> checkPickup(@PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.checkPickup(reservationId, principal == null ? null : principal.id()));
    }

    @PostMapping("/reservations/{reservationId}/loan")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReservationLoanResponse> createFromReservation(
            @PathVariable Long reservationId, @Valid @RequestBody CreateReservationLoanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal == null ? null : principal.id();
        // Keep the old service API path for clients that do not yet send a request key.
        if (request.overrideRequested()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(loans.createFromReservation(
                    reservationId, actorId, request.cardNumber(), request.expectedBorrowDate(),
                    request.expectedDueAt(), request.expectedLoanDays(), request.requestId(),
                    true, request.overrideReason()));
        }
        if (request.requestId() == null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(loans.createFromReservation(
                    reservationId, actorId, request.cardNumber(), request.expectedBorrowDate(),
                    request.expectedDueAt(), request.expectedLoanDays()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(loans.createFromReservation(
                reservationId, actorId, request.cardNumber(), request.expectedBorrowDate(),
                request.expectedDueAt(), request.expectedLoanDays(), request.requestId()));
    }

    @GetMapping("/loans/me/borrowed-books")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<List<MyBorrowedBookResponse>> myBorrowedBooks(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.myBorrowedBooks(principal == null ? null : principal.id()));
    }

    @GetMapping("/loans/me/returned-books")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<MyReturnedBooksPageResponse> myReturnedBooks(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.myReturnedBooks(principal == null ? null : principal.id(), page));
    }

    @GetMapping("/loans")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<LoanSummaryResponse>> listLoans(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.listLoans(principal == null ? null : principal.id()));
    }

    @GetMapping("/loans/reader-eligibility")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReaderLoanEligibilityResponse> readerEligibility(
            @RequestParam(defaultValue = "") String cardNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.readerEligibility(cardNumber, principal == null ? null : principal.id()));
    }

    @PostMapping("/loans/reader-eligibility/check")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReaderLoanEligibilityResponse> checkReaderAndLog(
            @Valid @RequestBody CheckReaderLoanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.checkReaderAndLog(
                request.cardNumber(), principal == null ? null : principal.id(), request.requestId()));
    }

    /** Viewer scope is provisional until PO decides permitted audit audiences. */
    @GetMapping("/loans/rejections")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanRejectionPageResponse> rejections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String cardNumber) {
        return ResponseEntity.ok(rejections.page(cardNumber, page, size));
    }

    @GetMapping("/loans/rejections/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanRejectionResponse> rejectionDetail(@PathVariable Long id) {
        return ResponseEntity.ok(rejections.detail(id));
    }

    @PostMapping("/loans/direct/items/preview")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<DirectLoanItemResponse> previewDirectLoanItem(
            @Valid @RequestBody AddDirectLoanItemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.previewDirectLoanItem(request, principal == null ? null : principal.id()));
    }

    @PostMapping("/loans/direct")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<DirectLoanResponse> createDirectLoan(
            @Valid @RequestBody CreateDirectLoanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loans.createDirectLoan(
                request, principal == null ? null : principal.id()));
    }

    @GetMapping("/loans/{loanId}")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LoanDetailResponse> loanDetail(@PathVariable Long loanId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loans.loanDetail(loanId, principal == null ? null : principal.id()));
    }
}
