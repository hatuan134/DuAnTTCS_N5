package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.account.SetInitialPasswordRequest;
import com.duanttcsn5.library.dto.account.ValidateInitialPasswordTokenResponse;
import com.duanttcsn5.library.service.InitialPasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/initial-password")
public class InitialPasswordController {

    private final InitialPasswordService initialPasswordService;

    public InitialPasswordController(InitialPasswordService initialPasswordService) {
        this.initialPasswordService = initialPasswordService;
    }

    @GetMapping
    public ResponseEntity<ValidateInitialPasswordTokenResponse> validateToken(@RequestParam String token) {
        return ResponseEntity.ok(initialPasswordService.validateToken(token));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> setInitialPassword(
            @Valid @RequestBody SetInitialPasswordRequest request,
            HttpServletRequest servletRequest) {
        initialPasswordService.setInitialPassword(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "message", "Thiết lập mật khẩu thành công. Bạn có thể đăng nhập ngay bây giờ."
        ));
    }
}
