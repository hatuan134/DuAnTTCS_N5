package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderProfileResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.dto.profile.ChangeReaderPasswordRequest;
import com.duanttcsn5.library.dto.profile.ChangeReaderPasswordResponse;
import com.duanttcsn5.library.dto.profile.ReaderSelfProfileResponse;
import com.duanttcsn5.library.dto.profile.UpdateReaderContactRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ReaderRegistrationService;
import com.duanttcsn5.library.service.ReaderSelfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/readers")
public class ReaderController {

    private final ReaderRegistrationService readerRegistrationService;
    private final ReaderSelfService readerSelfService;

    public ReaderController(ReaderRegistrationService readerRegistrationService,
                            ReaderSelfService readerSelfService) {
        this.readerRegistrationService = readerRegistrationService;
        this.readerSelfService = readerSelfService;
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
     * S1-06: Bạn đọc xem hồ sơ cá nhân và thông tin thẻ của chính mình.
     */
    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<ReaderSelfProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(readerSelfService.getProfile(principal.id()));
    }

    /**
     * S1-06: Cập nhật số điện thoại, địa chỉ và email.
     * Nếu email thay đổi thì bắt buộc xác nhận mật khẩu hiện tại.
     */
    @PutMapping("/me/contact")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<ReaderSelfProfileResponse> updateMyContact(
            @Valid @RequestBody UpdateReaderContactRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(readerSelfService.updateContact(
                principal.id(),
                request,
                httpRequest.getRemoteAddr()));
    }

    /**
     * S1-06: Đổi mật khẩu, không cho dùng lại 3 mật khẩu gần nhất.
     */
    @PutMapping("/me/password")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<ChangeReaderPasswordResponse> changeMyPassword(
            @Valid @RequestBody ChangeReaderPasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(readerSelfService.changePassword(
                principal.id(),
                request,
                httpRequest.getRemoteAddr()));
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
