package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderProfileResponse;
import com.duanttcsn5.library.dto.reader.ReaderLoanHistoryResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.LoanRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReaderRegistrationService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final LibraryCardRepository libraryCardRepository;
    private final LoanRepository loanRepository;

    public ReaderRegistrationService(UserRepository userRepository,
                                     ReaderProfileRepository readerProfileRepository,
                                     RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder,
                                     AuditLogRepository auditLogRepository,
                                     LibraryCardRepository libraryCardRepository,
                                     LoanRepository loanRepository) {
        this.userRepository = userRepository;
        this.readerProfileRepository = readerProfileRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
        this.libraryCardRepository = libraryCardRepository;
        this.loanRepository = loanRepository;
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

        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(LocalDate.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_OF_BIRTH",
                    "Ngày sinh không được vượt quá ngày hiện tại."
            );
        }

        // 1. Kiểm tra sự tồn tại của Email
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            String encodedEmail = URLEncoder.encode(normalizedEmail, StandardCharsets.UTF_8);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL",
                    "Email '" + request.email().trim() + "' đã được đăng ký trong hệ thống. Nếu bạn đã có tài khoản, vui lòng sử dụng chức năng Quên mật khẩu (chuyển hướng: /forgot-password?email=" + encodedEmail + ")."
            );
        }

        // 2. Lấy vai trò READER
        Role readerRole = roleRepository.findByCode("READER")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ROLE_NOT_FOUND",
                        "Không tìm thấy vai trò READER trong hệ thống."
                ));

        // 3. Tạo User
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

        // 4. Tạo ReaderProfile liên kết với User. Mã bạn đọc do hệ thống tự cấp.
        String generatedMemberCode = nextGeneratedMemberCode();
        ReaderProfile profile = new ReaderProfile();
        profile.setUser(savedUser);
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setMemberCode(generatedMemberCode);
        profile.setRegistrationStatus("PENDING");

        ReaderProfile savedProfile = readerProfileRepository.save(profile);

        // 5. Ghi nhật ký hệ thống (Audit Log)
        auditLogRepository.insert(
                savedUser.getId(),
                "READER_REGISTERED",
                "READER_PROFILE",
                savedUser.getId().toString(),
                "{\"memberCode\":\"" + generatedMemberCode + "\",\"email\":\"" + normalizedEmail + "\"}",
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


    private String nextGeneratedMemberCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            Long number = readerProfileRepository.nextMemberCodeNumber();
            if (number == null || number < 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "MEMBER_CODE_GENERATION_FAILED",
                        "Không thể sinh mã bạn đọc. Vui lòng thử lại."
                );
            }

            String candidate = String.format(Locale.ROOT, "BD%06d", number);
            if (!readerProfileRepository.existsByMemberCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }

        throw new ApiException(
                HttpStatus.CONFLICT,
                "MEMBER_CODE_GENERATION_RETRY",
                "Không thể cấp mã bạn đọc duy nhất. Vui lòng thử lại."
        );
    }

    @Transactional(readOnly = true)
    public List<ReaderProfileResponse> getAllReaders() {
        Map<Long, String> cardTypeByUserId = libraryCardRepository.findAllWithDetails().stream()
                .collect(Collectors.toMap(
                        card -> card.getUser().getId(),
                        card -> card.getCardType().getName(),
                        (first, ignored) -> first
                ));

        return readerProfileRepository.findAllWithUser().stream()
                .map(profile -> ReaderProfileResponse.fromEntity(
                        profile, cardTypeByUserId.get(profile.getUserId())))
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
        String cardTypeName = libraryCardRepository.findByUserIdWithDetails(profile.getUserId())
                .map(card -> card.getCardType().getName())
                .orElse(null);
        return ReaderProfileResponse.fromEntity(profile, cardTypeName);
    }

    /** S3-10.1: one loan counts once; any returned-late item marks that loan late.
     * Open overdue items do not count as historical late returns. Legacy items
     * without due dates remain visible but cannot be classified as returned late.
     */
    @Transactional(readOnly = true)
    public ReaderLoanHistoryResponse getReaderLoanHistory(Long id) {
        if (id == null || id < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_READER_ID",
                    "Mã bạn đọc phải là số nguyên dương.");
        }
        ReaderProfileResponse profile = getReaderById(id);
        Map<Long, List<LoanRepository.ReaderHistoryRow>> grouped = loanRepository.findReaderHistory(id)
                .stream().collect(Collectors.groupingBy(LoanRepository.ReaderHistoryRow::loanId,
                        LinkedHashMap::new, Collectors.toList()));
        ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
        List<ReaderLoanHistoryResponse.Loan> loans = grouped.values().stream().map(rows -> {
            var header = rows.get(0);
            List<ReaderLoanHistoryResponse.Item> items = rows.stream()
                    .filter(row -> row.itemId() != null)
                    .map(row -> {
                        boolean late = row.returnedAt() != null && row.dueAt() != null
                                && row.returnedAt().atZoneSameInstant(vietnam).toLocalDate()
                                .isAfter(row.dueAt().atZoneSameInstant(vietnam).toLocalDate());
                        return new ReaderLoanHistoryResponse.Item(row.itemId(), row.bookTitle(), row.barcode(),
                                row.borrowedAt(), row.dueAt(), row.returnedAt(),
                                row.returnedAt() == null ? "BORROWED" : "RETURNED", late);
                    }).toList();
            boolean open = items.stream().anyMatch(item -> item.returnedAt() == null);
            boolean returned = items.stream().anyMatch(item -> item.returnedAt() != null);
            String status = items.isEmpty() ? "EMPTY"
                    : open ? (returned ? "PARTIALLY_RETURNED" : "BORROWED") : "RETURNED";
            return new ReaderLoanHistoryResponse.Loan(header.loanId(), header.loanNumber(),
                    header.loanBorrowedAt(), status,
                    items.stream().anyMatch(ReaderLoanHistoryResponse.Item::returnedLate), items);
        }).toList();
        return new ReaderLoanHistoryResponse(profile,
                loans.stream().filter(loan -> "BORROWED".equals(loan.status())
                        || "PARTIALLY_RETURNED".equals(loan.status())).count(),
                loans.size(), loans.stream().filter(ReaderLoanHistoryResponse.Loan::returnedLate).count(), loans);
    }
}
