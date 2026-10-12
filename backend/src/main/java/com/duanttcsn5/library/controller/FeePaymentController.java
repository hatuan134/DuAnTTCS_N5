package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.CollectFullFeesRequest;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse;
import com.duanttcsn5.library.dto.loan.FeeReceiptResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.FeePaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fee-payments")
@PreAuthorize("hasRole('LIBRARIAN')")
public class FeePaymentController {
    private final FeePaymentService service;
    public FeePaymentController(FeePaymentService service) { this.service = service; }

    @GetMapping("/readers")
    public List<FeeCollectionResponse.Reader> search(@RequestParam String query, @AuthenticationPrincipal UserPrincipal actor) {
        return service.search(query, actor == null ? null : actor.id());
    }
    @GetMapping("/readers/{readerId}")
    public FeeCollectionResponse detail(@PathVariable Long readerId, @AuthenticationPrincipal UserPrincipal actor) {
        return service.detail(readerId, actor == null ? null : actor.id());
    }
    @PostMapping("/readers/{readerId}/collect-full")
    public FeeReceiptResponse collect(@PathVariable Long readerId, @Valid @RequestBody CollectFullFeesRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        // 200 also describes an idempotent replay of a previously committed collection.
        return service.collect(readerId, request, actor == null ? null : actor.id());
    }
    @GetMapping("/{paymentId}/receipt")
    public FeeReceiptResponse receipt(@PathVariable Long paymentId, @AuthenticationPrincipal UserPrincipal actor) {
        return service.receipt(paymentId, actor == null ? null : actor.id());
    }
}
