package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.account.AccountResponse;
import com.duanttcsn5.library.dto.account.CreateAccountRequest;
import com.duanttcsn5.library.dto.account.UpdateAccountStatusRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        Long adminId = principal != null ? principal.id() : null;
        String ipAddress = servletRequest.getRemoteAddr();
        AccountResponse response = userManagementService.createAccount(request, adminId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(userManagementService.getAccounts(search, role, status));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        Long adminId = principal != null ? principal.id() : null;
        String ipAddress = servletRequest.getRemoteAddr();
        AccountResponse response = userManagementService.updateAccountStatus(id, request.status(), adminId, ipAddress);
        return ResponseEntity.ok(response);
    }
}
