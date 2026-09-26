package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderProfileResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.service.ReaderRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/readers")
public class ReaderController {

    private final ReaderRegistrationService readerRegistrationService;

    public ReaderController(ReaderRegistrationService readerRegistrationService) {
        this.readerRegistrationService = readerRegistrationService;
    }

    /**
     * S1-03: API kiểm tra trùng lặp Email và Mã sinh viên/cán bộ.
     * Cho phép gọi public để hỗ trợ phản hồi tức thì trên giao diện đăng ký.
     */
    @GetMapping("/check-duplicate")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String memberCode) {
        return ResponseEntity.ok(readerRegistrationService.checkDuplicate(email, memberCode));
    }

    /**
     * S1-03: API tiếp nhận hồ sơ đăng ký bạn đọc.
     * Từ chối và trả về lỗi 409 Conflict kèm thông điệp gợi ý Quên mật khẩu nếu trùng lặp.
     */
    @PostMapping("/register")
    public ResponseEntity<ReaderRegistrationResponse> register(
            @Valid @RequestBody ReaderRegistrationRequest request,
            HttpServletRequest httpRequest) {
        ReaderRegistrationResponse response = readerRegistrationService.registerReader(
                request,
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Danh sách hồ sơ bạn đọc dành cho cán bộ thư viện / quản lý.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<ReaderProfileResponse>> getAllReaders() {
        return ResponseEntity.ok(readerRegistrationService.getAllReaders());
    }

    /**
     * Xem chi tiết hồ sơ bạn đọc theo ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<ReaderProfileResponse> getReaderById(@PathVariable Long id) {
        return ResponseEntity.ok(readerRegistrationService.getReaderById(id));
    }
}
