package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.profile.ChangePasswordRequest;
import com.duanttcsn5.library.dto.profile.ProfileMessageResponse;
import com.duanttcsn5.library.dto.profile.UpdateProfileRequest;
import com.duanttcsn5.library.dto.profile.UserProfileResponse;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.PasswordHistory;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.PasswordHistoryRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final LibraryCardRepository libraryCardRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;

    public ProfileService(UserRepository userRepository,
                          ReaderProfileRepository readerProfileRepository,
                          LibraryCardRepository libraryCardRepository,
                          PasswordHistoryRepository passwordHistoryRepository,
                          PasswordEncoder passwordEncoder,
                          AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.readerProfileRepository = readerProfileRepository;
        this.libraryCardRepository = libraryCardRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = getUserOrThrow(userId);
        ReaderProfile readerProfile = readerProfileRepository.findByUserId(userId).orElse(null);
        LibraryCard libraryCard = libraryCardRepository.findByUserId(userId).orElse(null);

        return buildProfileResponse(user, readerProfile, libraryCard);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request, String ipAddress) {
        User user = getUserOrThrow(userId);

        String newEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String currentEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean emailChanged = !newEmail.equalsIgnoreCase(currentEmail);

        if (emailChanged) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PASSWORD_REQUIRED",
                        "Bạn phải nhập mật khẩu hiện tại để đổi email."
                );
            }

            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PASSWORD",
                        "Mật khẩu hiện tại không chính xác."
                );
            }

            userRepository.findByEmailIgnoreCase(newEmail).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "EMAIL_ALREADY_EXISTS",
                            "Email này đã được sử dụng bởi tài khoản khác."
                    );
                }
            });

            user.setEmail(newEmail);
        }

        user.setPhone(request.phone() != null ? request.phone().trim() : null);
        user.setAddress(request.address() != null ? request.address().trim() : null);
        User savedUser = userRepository.save(user);

        String afterJson = String.format("{\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\"}",
                savedUser.getEmail(),
                savedUser.getPhone() != null ? savedUser.getPhone() : "",
                savedUser.getAddress() != null ? savedUser.getAddress() : "");
        auditLogRepository.insert(
                userId,
                "UPDATE_PROFILE",
                "USER",
                userId.toString(),
                afterJson,
                ipAddress
        );

        ReaderProfile readerProfile = readerProfileRepository.findByUserId(userId).orElse(null);
        LibraryCard libraryCard = libraryCardRepository.findByUserId(userId).orElse(null);

        return buildProfileResponse(savedUser, readerProfile, libraryCard);
    }

    @Transactional
    public ProfileMessageResponse changePassword(Long userId, ChangePasswordRequest request, String ipAddress) {
        User user = getUserOrThrow(userId);

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_MISMATCH",
                    "Xác nhận mật khẩu không khớp."
            );
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Mật khẩu hiện tại không chính xác."
            );
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_AS_CURRENT_PASSWORD",
                    "Mật khẩu mới phải khác mật khẩu hiện tại."
            );
        }

        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordHistory history : recentHistories) {
            if (passwordEncoder.matches(request.newPassword(), history.getPasswordHash())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PASSWORD_REUSED",
                        "Mật khẩu mới không được trùng 3 mật khẩu gần nhất."
                );
            }
        }

        PasswordHistory historyRecord = new PasswordHistory();
        historyRecord.setUserId(userId);
        historyRecord.setPasswordHash(user.getPasswordHash());
        historyRecord.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        passwordHistoryRepository.save(historyRecord);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditLogRepository.insert(
                userId,
                "CHANGE_PASSWORD",
                "USER",
                userId.toString(),
                "{\"result\":\"SUCCESS\"}",
                ipAddress
        );

        return new ProfileMessageResponse("Đổi mật khẩu thành công.");
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy thông tin người dùng."
                ));
    }

    private UserProfileResponse buildProfileResponse(User user, ReaderProfile readerProfile, LibraryCard libraryCard) {
        boolean hasCard = libraryCard != null;
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress(),
                readerProfile != null ? readerProfile.getDateOfBirth() : null,
                readerProfile != null ? readerProfile.getMemberCode() : null,
                user.getRole().getCode(),
                user.getRole().getName(),
                hasCard,
                hasCard ? libraryCard.getCardNumber() : null,
                hasCard && libraryCard.getCardType() != null ? libraryCard.getCardType().getName() : null,
                hasCard ? libraryCard.getStatus() : null,
                hasCard ? libraryCard.getIssuedAt() : null,
                hasCard ? libraryCard.getExpiresAt() : null
        );
    }
}
