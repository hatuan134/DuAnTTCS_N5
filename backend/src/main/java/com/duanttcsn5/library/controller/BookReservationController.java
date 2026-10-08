package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.book.BookReservationResponse;
import com.duanttcsn5.library.dto.book.BookReservationBatchResponse;
import com.duanttcsn5.library.dto.book.CreateBookReservationsRequest;
import com.duanttcsn5.library.dto.book.MyBookReservationResponse;
import com.duanttcsn5.library.dto.book.CancelBookReservationRequest;
import com.duanttcsn5.library.dto.book.CancelBookReservationResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duanttcsn5.library.dto.book.AutoCancelledReservationResponse;
import com.duanttcsn5.library.dto.book.AutoCancellationRunResponse;
import com.duanttcsn5.library.service.ReservationAutoCancellationService;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class BookReservationController {
    private final BookReservationService reservations;
    private final ReservationAutoCancellationService autoCancellationService;

    public BookReservationController(BookReservationService reservations) {
        this(reservations, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BookReservationController(
            BookReservationService reservations,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ReservationAutoCancellationService autoCancellationService) {
        this.reservations = reservations;
        this.autoCancellationService = autoCancellationService;
    }

    @GetMapping("/reservations/mine")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<List<MyBookReservationResponse>> getMine(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reservations.getMyReservations(principal == null ? null : principal.id()));
    }

    @PostMapping("/reservations/mine/{reservationId}/cancel")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<Void> cancelMine(
            @PathVariable Long reservationId, @AuthenticationPrincipal UserPrincipal principal) {
        reservations.cancelMine(reservationId, principal == null ? null : principal.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/books/{bookId}/reservations")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<BookReservationResponse> reserve(
            @PathVariable Long bookId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservations.reserve(bookId, principal == null ? null : principal.id()));
    }

    @PostMapping("/books/{bookId}/reservations/bulk")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<BookReservationBatchResponse> reserveMany(
            @PathVariable Long bookId,
            @Valid @RequestBody CreateBookReservationsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservations.reserveMany(bookId, principal == null ? null : principal.id(), request.quantity()));
    }

    @GetMapping("/books/{bookId}/reservations")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<BookReservationQueueResponse> getQueueByBookId(
            @PathVariable Long bookId, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(reservations.getQueueByBookId(bookId, status));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CancelBookReservationResponse> cancelByStaff(
            @PathVariable Long reservationId, @Valid @RequestBody CancelBookReservationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reservations.cancelByStaff(reservationId,
                principal == null ? null : principal.id(), request.reason()));
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

    @PostMapping("/reservations/auto-cancel-overdue")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AutoCancelledReservationResponse>> autoCancelOverdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime checkTime) {
        if (autoCancellationService == null) {
            return ResponseEntity.ok(List.of());
        }
        if (checkTime != null) {
            return ResponseEntity.ok(autoCancellationService.processOverdueReservationsAt(checkTime));
        }
        return ResponseEntity.ok(autoCancellationService.processOverdueReservations());
    }

    /**
     * S3-06.3: Hiển thị kết quả của lần chạy gần nhất cho Quản lý kiểm tra.
     */
    @GetMapping("/reservations/auto-cancel-runs/latest")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AutoCancellationRunResponse> getLatestAutoCancelRun() {
        if (autoCancellationService == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<AutoCancellationRunResponse> latest = autoCancellationService.getLatestRun();
        return latest.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * S3-06.3: Lấy danh sách lịch sử tất cả các lần chạy tự động cho Quản lý đối chiếu.
     */
    @GetMapping("/reservations/auto-cancel-runs")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<AutoCancellationRunResponse>> getAllAutoCancelRuns() {
        if (autoCancellationService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(autoCancellationService.getAllRuns());
    }

    /**
     * S3-06.3: Kích hoạt lần chạy kiểm tra đơn quá hạn và trả về đầy đủ số liệu thống kê của lần chạy.
     */
    @PostMapping("/reservations/auto-cancel-runs/trigger")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<AutoCancellationRunResponse> triggerAutoCancelRun(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime checkTime,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (autoCancellationService == null) {
            return ResponseEntity.noContent().build();
        }
        String actor = currentUser != null ? currentUser.email() : "MANUAL_TRIGGER";
        OffsetDateTime time = checkTime != null ? checkTime : OffsetDateTime.now();
        return ResponseEntity.ok(autoCancellationService.executeAutoCancellationRun(time, actor));
    }
}
