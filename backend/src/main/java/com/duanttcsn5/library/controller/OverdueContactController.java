package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.CreateOverdueContactRequest;
import com.duanttcsn5.library.dto.loan.OverdueContactResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.OverdueContactService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans/{loanId}/overdue-contacts")
public class OverdueContactController {
    private final OverdueContactService service;

    public OverdueContactController(OverdueContactService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<OverdueContactResponse>> history(
            @PathVariable Long loanId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.history(loanId, principal == null ? null : principal.id()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<OverdueContactResponse> record(
            @PathVariable Long loanId, @Valid @RequestBody CreateOverdueContactRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.record(loanId, request, principal == null ? null : principal.id()));
    }
}
