package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderProfileResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ReaderRegistrationService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;

    public ReaderRegistrationService(UserRepository userRepository,
                                     ReaderProfileRepository readerProfileRepository,
                                     RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder,
                                     AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.readerProfileRepository = readerProfileRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * S1-03: Kiểm tra trùng lặp Email và Mã sinh viên/Mã cán bộ trước khi lưu.
     */
    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkDuplicate(String rawEmail, String rawMemberCode) {
        boolean emailExists = false;
        String emailMessage = null;
        String forgotPasswordUrl = "/forgot-password";

        if (rawEmail != null && !rawEmail.trim().isEmpty()) {
            String email = rawEmail.trim().toLowerCase(Locale.ROOT);
            emailExists = userRepository.existsByEmailIgnoreCase(email);
            if (emailExists) {
                emailMessage = "Email '" + rawEmail.trim() + "' đã tồn tại trong hệ thống. Bạn có thể đã có tài khoản, vui lòng sử dụng chức năng Quên mật khẩu để khôi phục.";
                forgotPasswordUrl = "/forgot-password?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
            }
        }

        boolean memberCodeExists = false;
        String memberCodeMessage = null;
        if (rawMemberCode != null && !rawMemberCode.trim().isEmpty()) {
            String memberCode = rawMemberCode.trim();
            memberCodeExists = readerProfileRepository.existsByMemberCodeIgnoreCase(memberCode);
            if (memberCodeExists) {
                memberCodeMessage = "Mã sinh viên/cán bộ '" + memberCode + "' đã được đăng ký hồ sơ trong hệ thống. Vui lòng sử dụng chức năng Quên mật khẩu hoặc liên hệ thủ thư.";
            }
        }

        boolean suggestForgotPassword = emailExists || memberCodeExists;

        return new DuplicateCheckResponse(
                emailExists,
                memberCodeExists,
                emailMessage,
                memberCodeMessage,
                suggestForgotPassword,
                forgotPasswordUrl
        );
    }

    /**
     * S1-03: Xử lý đăng ký bạn đọc, từ chối lưu hồ sơ và trả về thông báo lỗi nếu trùng lặp.
     */
    @Transactional(rollbackFor = Exception.class)
    public ReaderRegistrationResponse registerReader(ReaderRegistrationRequest request, String ipAddress) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedMemberCode = request.memberCode().trim().toUpperCase(Locale.ROOT);

        // 1. Kiểm tra sự tồn tại của Email
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            String encodedEmail = URLEncoder.encode(normalizedEmail, StandardCharsets.UTF_8);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL",
                    "Email '" + request.email().trim() + "' đã được đăng ký trong hệ thống. Nếu bạn đã có tài khoản, vui lòng sử dụng chức năng Quên mật khẩu (chuyển hướng: /forgot-password?email=" + encodedEmail + ")."
            );
        }

        // 2. Kiểm tra sự tồn tại của Mã sinh viên/cán bộ
        if (readerProfileRepository.existsByMemberCodeIgnoreCase(normalizedMemberCode)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_MEMBER_CODE",
                    "Mã sinh viên/cán bộ '" + request.memberCode().trim() + "' đã được đăng ký hồ sơ bạn đọc. Vui lòng sử dụng chức năng Quên mật khẩu hoặc liên hệ thủ thư thư viện."
            );
        }

        // 3. Lấy vai trò READER
        Role readerRole = roleRepository.findByCode("READER")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ROLE_NOT_FOUND",
                        "Không tìm thấy vai trò READER trong hệ thống."
                ));

        // 4. Tạo User
        User user = new User();
        user.setRole(readerRole);
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPhone(request.phone() != null ? request.phone().trim() : null);
        user.setAddress(request.address() != null ? request.address().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");
        user.setFailedLoginAttempts(0);
        user.setTokenVersion(0);

        User savedUser = userRepository.save(user);

        // 5. Tạo ReaderProfile liên kết với User
        ReaderProfile profile = new ReaderProfile();
        profile.setUser(savedUser);
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setMemberCode(normalizedMemberCode);
        profile.setRegistrationStatus("PENDING");

        ReaderProfile savedProfile = readerProfileRepository.save(profile);

        // 6. Ghi nhật ký hệ thống (Audit Log)
        auditLogRepository.insert(
                savedUser.getId(),
                "READER_REGISTERED",
                "READER_PROFILE",
                savedUser.getId().toString(),
                "{\"memberCode\":\"" + normalizedMemberCode + "\",\"email\":\"" + normalizedEmail + "\"}",
                ipAddress
        );

        return new ReaderRegistrationResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedProfile.getMemberCode(),
                savedProfile.getRegistrationStatus(),
                savedProfile.getSubmittedAt() != null ? savedProfile.getSubmittedAt() : OffsetDateTime.now(),
                "Đăng ký tài khoản bạn đọc thành công! Hồ sơ đang ở trạng thái chờ duyệt."
        );
    }

    @Transactional(readOnly = true)
    public List<ReaderProfileResponse> getAllReaders() {
        return readerProfileRepository.findAllWithUser().stream()
                .map(ReaderProfileResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReaderProfileResponse getReaderById(Long id) {
        ReaderProfile profile = readerProfileRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "READER_NOT_FOUND",
                        "Không tìm thấy hồ sơ bạn đọc ID: " + id
                ));
        return ReaderProfileResponse.fromEntity(profile);
    }
}
