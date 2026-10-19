package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.service.PermissionReviewGitHubCallbackService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import com.duanttcsn5.library.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Excluded from public /api/v1 business role matrix; accepts only a signed CI result. */
@RestController
@RequestMapping("/api/internal/permission-review/callback")
public class PermissionReviewGitHubCallbackController {
    private final PermissionReviewGitHubCallbackService service;
    public PermissionReviewGitHubCallbackController(PermissionReviewGitHubCallbackService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> callback(@RequestHeader(value = "X-Libra-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Libra-Signature", required = false) String signature,
            HttpServletRequest request) throws IOException {
        // Read at most 8 MiB + 1 byte even for chunked requests; never buffer an unlimited public body.
        byte[] bytes = request.getInputStream().readNBytes(8 * 1024 * 1024 + 1);
        if (bytes.length > 8 * 1024 * 1024)
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "REVIEW_CALLBACK_TOO_LARGE", "Kết quả kiểm thử vượt giới hạn.");
        service.accept(timestamp, signature, new String(bytes, StandardCharsets.UTF_8));
        return ResponseEntity.noContent().build();
    }
}
