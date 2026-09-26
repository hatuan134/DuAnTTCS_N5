package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.cardtype.CardTypeResponse;
import com.duanttcsn5.library.dto.cardtype.CreateCardTypeRequest;
import com.duanttcsn5.library.dto.cardtype.PolicyHistoryResponse;
import com.duanttcsn5.library.dto.cardtype.UpdateCardTypeRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.CardTypeService;
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
@RequestMapping("/api/v1/card-types")
public class CardTypeController {

    private final CardTypeService cardTypeService;

    public CardTypeController(CardTypeService cardTypeService) {
        this.cardTypeService = cardTypeService;
    }

    @GetMapping
    public ResponseEntity<List<CardTypeResponse>> getAllCardTypes() {
        return ResponseEntity.ok(cardTypeService.getAllCardTypes());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CardTypeResponse>> getActiveCardTypes() {
        return ResponseEntity.ok(cardTypeService.getActiveCardTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardTypeResponse> getCardTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(cardTypeService.getCardTypeById(id));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<PolicyHistoryResponse>> getPolicyHistory() {
        return ResponseEntity.ok(cardTypeService.getPolicyHistory());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> createCardType(
            @Valid @RequestBody CreateCardTypeRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.createCardType(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> updateCardType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCardTypeRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.updateCardType(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CardTypeResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CardTypeResponse response = cardTypeService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteCardType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        cardTypeService.deleteCardType(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
