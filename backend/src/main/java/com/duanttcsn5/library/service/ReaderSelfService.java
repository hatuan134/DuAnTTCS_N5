package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.profile.ChangeReaderPasswordRequest;
import com.duanttcsn5.library.dto.profile.ChangeReaderPasswordResponse;
import com.duanttcsn5.library.dto.profile.ReaderSelfProfileResponse;
import com.duanttcsn5.library.dto.profile.UpdateReaderContactRequest;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.PasswordHistory;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.PasswordHistoryRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReaderSelfService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final LibraryCardRepository libraryCardRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public ReaderSelfService(UserRepository userRepository,
                             ReaderProfileRepository readerProfileRepository,
                             LibraryCardRepository libraryCardRepository,
                             PasswordHistoryRepository passwordHistoryRepository,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.readerProfileRepository = readerProfileRepository;
        this.libraryCardRepository = libraryCardRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public ReaderSelfProfileResponse getProfile(Long userId) {
        User user = getUser(userId);
        ReaderProfile profile = getReaderProfile(userId);
        LibraryCard card = libraryCardRepository.findByUserIdWithDetails(userId).orElse(null);
        return toResponse(user, profile, card);
    }

    @Transactional
    public ReaderSelfProfileResponse updateContact(Long userId,
                                                   UpdateReaderContactRequest request,
                                                   String ipAddress) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản người dùng."));

        ReaderProfile profile = getReaderProfile(userId);

        String normalizedPhone = request.phone().trim();
        String normalizedAddress = request.address().trim();
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String oldEmail = user.getEmail();
        boolean emailChanged = !normalizedEmail.equalsIgnoreCase(oldEmail);

        if (emailChanged) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "EMAIL_PASSWORD_REQUIRED",
                        "Bạn phải nhập mật khẩu hiện tại để đổi email.");
            }

            if (user.getPasswordHash() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CURRENT_PASSWORD",
                        "Mật khẩu hiện tại không chính xác.");
            }

            if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, userId)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "EMAIL_ALREADY_EXISTS",
                        "Email mới đã được sử dụng bởi một tài khoản khác.");
            }
        }

        boolean phoneChanged = !safeEquals(user.getPhone(), normalizedPhone);
        boolean addressChanged = !safeEquals(user.getAddress(), normalizedAddress);

        user.setPhone(normalizedPhone);
        user.setAddress(normalizedAddress);
        user.setEmail(normalizedEmail);
        userRepository.save(user);

        auditLogService.logReaderContactUpdated(
                userId,
                oldEmail,
                normalizedEmail,
                emailChanged,
                phoneChanged,
                addressChanged,
                ipAddress);

        LibraryCard card = libraryCardRepository.findByUserIdWithDetails(userId).orElse(null);
        return toResponse(user, profile, card);
    }

    @Transactional
    public ChangeReaderPasswordResponse changePassword(Long userId,
                                                       ChangeReaderPasswordRequest request,
                                                       String ipAddress) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản người dùng."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CURRENT_PASSWORD",
                    "Mật khẩu hiện tại không chính xác.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "Xác nhận mật khẩu mới không khớp.");
        }

        validatePasswordComplexity(request.newPassword());

        List<String> lastThreeHashes = lastThreePasswordHashes(user);
        boolean reused = lastThreeHashes.stream()
                .anyMatch(hash -> passwordEncoder.matches(request.newPassword(), hash));

        if (reused) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_RECENTLY_USED",
                    "Mật khẩu mới không được trùng với 3 mật khẩu gần nhất.");
        }

        String oldPasswordHash = user.getPasswordHash();
        if (!passwordHistoryRepository.existsByUserIdAndPasswordHash(userId, oldPasswordHash)) {
            saveHistory(userId, oldPasswordHash);
        }

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newPasswordHash);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        saveHistory(userId, newPasswordHash);
        auditLogService.logReaderPasswordChanged(userId, user.getEmail(), ipAddress);

        return new ChangeReaderPasswordResponse("Đổi mật khẩu thành công.");
    }

    private void validatePasswordComplexity(String password) {
        boolean validLength = password.length() >= 8 && password.length() <= 72;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);

        if (!validLength || !hasLetter || !hasDigit) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_TOO_WEAK",
                    "Mật khẩu mới phải có từ 8 đến 72 ký tự và chứa cả chữ lẫn số.");
        }
    }

    private List<String> lastThreePasswordHashes(User user) {
        List<String> result = new ArrayList<>();
        result.add(user.getPasswordHash());

        for (PasswordHistory history : passwordHistoryRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(user.getId())) {
            String hash = history.getPasswordHash();
            if (hash != null && !result.contains(hash)) {
                result.add(hash);
            }
            if (result.size() == 3) {
                break;
            }
        }

        return result;
    }

    private void saveHistory(Long userId, String passwordHash) {
        PasswordHistory history = new PasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(passwordHash);
        passwordHistoryRepository.save(history);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản người dùng."));
    }

    private ReaderProfile getReaderProfile(Long userId) {
        return readerProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "READER_PROFILE_NOT_FOUND",
                        "Không tìm thấy hồ sơ bạn đọc của tài khoản hiện tại."));
    }

    private ReaderSelfProfileResponse toResponse(User user,
                                                 ReaderProfile profile,
                                                 LibraryCard card) {
        return new ReaderSelfProfileResponse(
                user.getId(),
                user.getFullName(),
                profile.getDateOfBirth(),
                profile.getMemberCode(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress(),
                profile.getRegistrationStatus(),
                profile.getRejectionReason(),
                card == null ? null : card.getCardNumber(),
                card == null ? null : card.getCardType().getName(),
                card == null ? null : card.getIssuedAt(),
                card == null ? null : card.getExpiresAt(),
                card == null ? null : card.getStatus());
    }

    private boolean safeEquals(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }
}
