package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.profile.ChangePasswordRequest;
import com.duanttcsn5.library.dto.profile.ProfileMessageResponse;
import com.duanttcsn5.library.dto.profile.UpdateProfileRequest;
import com.duanttcsn5.library.dto.profile.UserProfileResponse;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByCode("READER").orElseGet(() -> {
            Role r = new Role();
            r.setCode("READER");
            r.setName("Bạn đọc");
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setFullName("Nguyễn Văn An");
        testUser.setEmail("nguyenvanan.test@thuvien.local");
        testUser.setPhone("0912345678");
        testUser.setAddress("Hà Nội");
        testUser.setPasswordHash(passwordEncoder.encode("OldPassword123"));
        testUser.setRole(role);
        testUser.setStatus("ACTIVE");
        testUser.setFailedLoginAttempts(0);
        testUser.setTokenVersion(0);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("S1-06-T1: Lấy thông tin hồ sơ cá nhân thành công")
    void testGetProfileSuccess() {
        UserProfileResponse profile = profileService.getProfile(testUser.getId());
        assertNotNull(profile);
        assertEquals(testUser.getFullName(), profile.fullName());
        assertEquals(testUser.getEmail(), profile.email());
        assertEquals("0912345678", profile.phone());
        assertEquals("Hà Nội", profile.address());
        assertEquals("READER", profile.role());
    }

    @Test
    @DisplayName("S1-06-T1: Cập nhật số điện thoại và địa chỉ thành công không cần nhập mật khẩu nếu email giữ nguyên")
    void testUpdatePhoneAndAddressSuccess() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                testUser.getEmail(),
                "0987654321",
                "TP Hồ Chí Minh",
                null
        );

        UserProfileResponse updated = profileService.updateProfile(testUser.getId(), request, "127.0.0.1");
        assertEquals("0987654321", updated.phone());
        assertEquals("TP Hồ Chí Minh", updated.address());
        assertEquals(testUser.getEmail(), updated.email());
    }

    @Test
    @DisplayName("S1-06-T2: Đổi email nhưng không nhập mật khẩu hiện tại phải bị từ chối")
    void testChangeEmailWithoutPasswordFails() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "an.newemail@thuvien.local",
                "0912345678",
                "Hà Nội",
                null
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.updateProfile(testUser.getId(), request, "127.0.0.1"));
        assertEquals("PASSWORD_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("S1-06-T2: Đổi email nhưng nhập sai mật khẩu hiện tại phải bị từ chối")
    void testChangeEmailWithWrongPasswordFails() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "an.newemail@thuvien.local",
                "0912345678",
                "Hà Nội",
                "WrongPassword123"
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.updateProfile(testUser.getId(), request, "127.0.0.1"));
        assertEquals("INVALID_PASSWORD", ex.getCode());
    }

    @Test
    @DisplayName("S1-06-T2: Đổi email thành công khi nhập đúng mật khẩu hiện tại")
    void testChangeEmailWithCorrectPasswordSuccess() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "an.newemail@thuvien.local",
                "0912345678",
                "Hà Nội",
                "OldPassword123"
        );

        UserProfileResponse updated = profileService.updateProfile(testUser.getId(), request, "127.0.0.1");
        assertEquals("an.newemail@thuvien.local", updated.email());
    }

    @Test
    @DisplayName("S1-06-T3: Đổi mật khẩu nhưng xác nhận mật khẩu không khớp phải bị từ chối")
    void testChangePasswordMismatchFails() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "NewPassword2026",
                "DifferentPassword2026"
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.changePassword(testUser.getId(), request, "127.0.0.1"));
        assertEquals("PASSWORD_MISMATCH", ex.getCode());
    }

    @Test
    @DisplayName("S1-06-T3: Đổi mật khẩu nhưng nhập sai mật khẩu hiện tại phải bị từ chối")
    void testChangePasswordWrongCurrentFails() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongCurrentPass1",
                "NewPassword2026",
                "NewPassword2026"
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.changePassword(testUser.getId(), request, "127.0.0.1"));
        assertEquals("INVALID_PASSWORD", ex.getCode());
    }

    @Test
    @DisplayName("S1-06-T3: Đổi mật khẩu trùng với mật khẩu hiện tại phải bị từ chối")
    void testChangePasswordSameAsCurrentFails() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "OldPassword123",
                "OldPassword123"
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.changePassword(testUser.getId(), request, "127.0.0.1"));
        assertEquals("SAME_AS_CURRENT_PASSWORD", ex.getCode());
    }

    @Test
    @DisplayName("S1-06-T3: Đổi mật khẩu hợp lệ lần đầu thành công")
    void testChangePasswordValidSuccess() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "NewPassword2026",
                "NewPassword2026"
        );

        ProfileMessageResponse response = profileService.changePassword(testUser.getId(), request, "127.0.0.1");
        assertNotNull(response);
        assertEquals("Đổi mật khẩu thành công.", response.message());

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPassword2026", reloaded.getPasswordHash()));
        assertEquals(1, reloaded.getTokenVersion());
    }

    @Test
    @DisplayName("S1-06-T3: Đổi mật khẩu mới không được trùng với 3 mật khẩu gần nhất")
    void testChangePasswordReusedHistoryFails() {
        // Đổi lần 1: OldPassword123 -> PasswordB1
        profileService.changePassword(testUser.getId(),
                new ChangePasswordRequest("OldPassword123", "PasswordB1", "PasswordB1"), "127.0.0.1");

        // Đổi lần 2: Thử dùng lại OldPassword123 -> phải bị từ chối
        ApiException ex = assertThrows(ApiException.class, () ->
                profileService.changePassword(testUser.getId(),
                        new ChangePasswordRequest("PasswordB1", "OldPassword123", "OldPassword123"), "127.0.0.1"));
        assertEquals("PASSWORD_REUSED", ex.getCode());
    }
}
