package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.auth.ForgotPasswordRequest;
import com.duanttcsn5.library.dto.auth.ForgotPasswordResponse;
import com.duanttcsn5.library.dto.auth.ResetPasswordRequest;
import com.duanttcsn5.library.dto.auth.ResetPasswordResponse;
import com.duanttcsn5.library.dto.auth.ValidateResetPasswordTokenResponse;
import com.duanttcsn5.library.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(passwordResetService.requestPasswordReset(request, servletRequest.getRemoteAddr()));
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<ValidateResetPasswordTokenResponse> validateResetPasswordToken(
            @RequestParam String token) {
        return ResponseEntity.ok(passwordResetService.validateToken(token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(passwordResetService.resetPassword(request, servletRequest.getRemoteAddr()));
    }
}
