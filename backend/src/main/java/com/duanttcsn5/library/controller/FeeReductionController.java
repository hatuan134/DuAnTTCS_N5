package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.CreateFeeReductionRequest;
import com.duanttcsn5.library.dto.loan.FeeReductionRequestPageResponse;
import com.duanttcsn5.library.dto.loan.FeeReductionRequestResponse;
import com.duanttcsn5.library.dto.loan.OverdueFeeDetailResponse;
import com.duanttcsn5.library.dto.loan.RejectFeeReductionRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.FeeReductionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/overdue-fees")
public class FeeReductionController {
    private final FeeReductionService service;
    public FeeReductionController(FeeReductionService service) { this.service = service; }

    @GetMapping("/{feeId}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<OverdueFeeDetailResponse> detail(@PathVariable Long feeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.detail(feeId, principal == null ? null : principal.id()));
    }

    @PostMapping("/{feeId}/reduction-requests")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<FeeReductionRequestResponse> create(@PathVariable Long feeId,
            @Valid @RequestBody CreateFeeReductionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(feeId, request, principal == null ? null : principal.id()));
    }

    @GetMapping("/reduction-requests/pending")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<FeeReductionRequestPageResponse> pending(@RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.pending(page, principal == null ? null : principal.id()));
    }

    @PostMapping("/reduction-requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<FeeReductionRequestResponse> approve(@PathVariable Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.approve(requestId, principal == null ? null : principal.id()));
    }

    @PostMapping("/reduction-requests/{requestId}/reject")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<FeeReductionRequestResponse> reject(@PathVariable Long requestId,
            @Valid @RequestBody RejectFeeReductionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.reject(requestId, request, principal == null ? null : principal.id()));
    }
}
