package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.account.AccountResponse;
import com.duanttcsn5.library.dto.account.CreateAccountRequest;
import com.duanttcsn5.library.dto.account.SetInitialPasswordRequest;
import com.duanttcsn5.library.dto.account.ValidateInitialPasswordTokenResponse;
import com.duanttcsn5.library.entity.PasswordResetRequest;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PasswordResetRequestRepository;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.AuditLogService;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.InitialPasswordService;
import com.duanttcsn5.library.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetRequestRepository passwordResetRequestRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private UserManagementService userManagementService;
    private InitialPasswordService initialPasswordService;

    @BeforeEach
    void setUp() {
        userManagementService = new UserManagementService(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                passwordResetRequestRepository,
                emailService,
                auditLogService);

        initialPasswordService = new InitialPasswordService(
                passwordResetRequestRepository,
                userRepository,
                passwordEncoder,
                auditLogService,
                jdbcTemplate);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("CASE 1: Admin tạo tài khoản Thủ thư hợp lệ => thành công và gửi email")
    void testCreateAccount_Librarian_Success() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Nguyễn Văn Thủ Thư",
                "thuthu@libra.edu.vn",
                "0987654321",
                "LIBRARIAN",
                "ACTIVE");

        Role librarianRole = new Role();
        librarianRole.setId(2L);
        librarianRole.setCode("LIBRARIAN");
        librarianRole.setName("Thủ thư");

        when(userRepository.existsByEmailIgnoreCase("thuthu@libra.edu.vn")).thenReturn(false);
        when(roleRepository.findByCode("LIBRARIAN")).thenReturn(Optional.of(librarianRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        AccountResponse response = userManagementService.createAccount(request, 1L, "127.0.0.1");

        assertNotNull(response);
        assertEquals("Nguyễn Văn Thủ Thư", response.fullName());
        assertEquals("thuthu@libra.edu.vn", response.email());
        assertEquals("LIBRARIAN", response.role());
        assertEquals("Thủ thư", response.roleName());
        assertEquals("ACTIVE", response.status());

        // CASE 7: Tài khoản mới nhận được email đặt mật khẩu
        verify(emailService, times(1)).sendInitialPasswordEmail(eq("thuthu@libra.edu.vn"), eq("Nguyễn Văn Thủ Thư"), anyString());
        verify(auditLogService, times(1)).logUserCreated(eq(1L), eq(10L), eq("thuthu@libra.edu.vn"), eq("LIBRARIAN"), eq("127.0.0.1"));
        verify(passwordResetRequestRepository, times(1)).save(any(PasswordResetRequest.class));
    }

    @Test
    @DisplayName("CASE 2: Admin tạo tài khoản Quản lý thư viện => thành công")
    void testCreateAccount_LibraryManager_Success() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Trần Quản Lý",
                "quanly@libra.edu.vn",
                "0912345678",
                "LIBRARY_MANAGER",
                "ACTIVE");

        Role managerRole = new Role();
        managerRole.setId(3L);
        managerRole.setCode("LIBRARY_MANAGER");
        managerRole.setName("Quản lý thư viện");

        when(userRepository.existsByEmailIgnoreCase("quanly@libra.edu.vn")).thenReturn(false);
        when(roleRepository.findByCode("LIBRARY_MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(11L);
            return u;
        });

        AccountResponse response = userManagementService.createAccount(request, 1L, "127.0.0.1");

        assertNotNull(response);
        assertEquals("LIBRARY_MANAGER", response.role());
        assertEquals("Quản lý thư viện", response.roleName());
        verify(emailService, times(1)).sendInitialPasswordEmail(eq("quanly@libra.edu.vn"), anyString(), anyString());
    }

    @Test
    @DisplayName("CASE 3: Admin tạo tài khoản Quản trị hệ thống => thành công")
    void testCreateAccount_Admin_Success() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Lê Quản Trị",
                "admin2@libra.edu.vn",
                "0933333333",
                "ADMIN",
                "ACTIVE");

        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode("ADMIN");
        adminRole.setName("Quản trị hệ thống");

        when(userRepository.existsByEmailIgnoreCase("admin2@libra.edu.vn")).thenReturn(false);
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(12L);
            return u;
        });

        AccountResponse response = userManagementService.createAccount(request, 1L, "127.0.0.1");

        assertNotNull(response);
        assertEquals("ADMIN", response.role());
        assertEquals("Quản trị hệ thống", response.roleName());
    }

    @Test
    @DisplayName("CASE 4: Email đã tồn tại => từ chối (409 CONFLICT)")
    void testCreateAccount_DuplicateEmail_ThrowsConflict() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Người Dùng Mới",
                "exists@libra.edu.vn",
                "0911111111",
                "LIBRARIAN",
                "ACTIVE");

        when(userRepository.existsByEmailIgnoreCase("exists@libra.edu.vn")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                userManagementService.createAccount(request, 1L, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DUPLICATE_EMAIL", ex.getCode());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendInitialPasswordEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("CASE 5: Gửi role không hợp lệ (READER hoặc khác) => từ chối (400 BAD_REQUEST)")
    void testCreateAccount_InvalidRole_ThrowsBadRequest() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Người Dùng Bạn Đọc",
                "reader@libra.edu.vn",
                "0911111111",
                "READER", // Không được tạo bạn đọc từ chức năng tài khoản nhân viên
                "ACTIVE");

        when(userRepository.existsByEmailIgnoreCase("reader@libra.edu.vn")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                userManagementService.createAccount(request, 1L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_ROLE", ex.getCode());
    }

    @Test
    @DisplayName("CASE 8: Token đặt mật khẩu hợp lệ => đặt mật khẩu thành công")
    void testSetInitialPassword_ValidToken_Success() {
        String rawToken = "my-valid-secret-token-123456";
        String tokenHash = sha256(rawToken);

        User user = new User();
        user.setId(20L);
        user.setEmail("user20@libra.edu.vn");
        user.setFullName("User Hai Mươi");
        user.setStatus("ACTIVE");
        user.setTokenVersion(0);

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUser(user);
        resetRequest.setRequestedEmail(user.getEmail());
        resetRequest.setTokenHash(tokenHash);
        resetRequest.setRequestType("INITIAL_PASSWORD");
        resetRequest.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        resetRequest.setUsedAt(null);

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "INITIAL_PASSWORD"))
                .thenReturn(Optional.of(resetRequest));
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("hashed-pwd-xyz");

        SetInitialPasswordRequest req = new SetInitialPasswordRequest(rawToken, "NewPassword@123");
        assertDoesNotThrow(() -> initialPasswordService.setInitialPassword(req, "127.0.0.1"));

        assertNotNull(resetRequest.getUsedAt());
        assertEquals("hashed-pwd-xyz", user.getPasswordHash());
        assertEquals(1, user.getTokenVersion());
        verify(userRepository, times(1)).save(user);
        verify(passwordResetRequestRepository, times(1)).save(resetRequest);
        verify(auditLogService, times(1)).logInitialPasswordSet(eq(20L), eq("user20@libra.edu.vn"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("CASE 9: Token đã sử dụng => không sử dụng lần 2 (400 BAD_REQUEST)")
    void testSetInitialPassword_TokenAlreadyUsed_ThrowsBadRequest() {
        String rawToken = "already-used-token";
        String tokenHash = sha256(rawToken);

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setTokenHash(tokenHash);
        resetRequest.setRequestType("INITIAL_PASSWORD");
        resetRequest.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(20));
        resetRequest.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)); // Đã dùng trước đó

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "INITIAL_PASSWORD"))
                .thenReturn(Optional.of(resetRequest));

        SetInitialPasswordRequest req = new SetInitialPasswordRequest(rawToken, "NewPassword@123");
        ApiException ex = assertThrows(ApiException.class, () ->
                initialPasswordService.setInitialPassword(req, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("TOKEN_ALREADY_USED", ex.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("CASE 10: Token quá 24 giờ => từ chối (400 BAD_REQUEST)")
    void testSetInitialPassword_TokenExpired_ThrowsBadRequest() {
        String rawToken = "expired-token-after-24h";
        String tokenHash = sha256(rawToken);

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setTokenHash(tokenHash);
        resetRequest.setRequestType("INITIAL_PASSWORD");
        resetRequest.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)); // Hết hạn
        resetRequest.setUsedAt(null);

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "INITIAL_PASSWORD"))
                .thenReturn(Optional.of(resetRequest));

        SetInitialPasswordRequest req = new SetInitialPasswordRequest(rawToken, "NewPassword@123");
        ApiException ex = assertThrows(ApiException.class, () ->
                initialPasswordService.setInitialPassword(req, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("TOKEN_EXPIRED", ex.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("CASE 12: Admin khóa tài khoản => tăng tokenVersion và thu hồi toàn bộ refresh token")
    void testLockAccount_IncrementsTokenVersionAndRevokesRefreshTokens() {
        Role role = new Role();
        role.setId(2L);
        role.setCode("LIBRARIAN");
        role.setName("Thủ thư");

        User user = new User();
        user.setId(50L);
        user.setRole(role);
        user.setEmail("user50@libra.edu.vn");
        user.setStatus("ACTIVE");
        user.setTokenVersion(1);

        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AccountResponse response = userManagementService.updateAccountStatus(50L, "LOCKED", 1L, "127.0.0.1");

        assertEquals("LOCKED", response.status());
        assertEquals(2, user.getTokenVersion(), "Token version phải tăng để làm mất hiệu lực token hiện tại ngay lập tức");
        verify(refreshTokenRepository, times(1)).revokeAllActiveByUserId(eq(50L), any(OffsetDateTime.class));
        verify(auditLogService, times(1)).logUserStatusUpdated(eq(1L), eq(50L), eq("ACTIVE"), eq("LOCKED"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("CASE 14: Mở khóa tài khoản => đặt status=ACTIVE, reset lockedUntil và failedLoginAttempts")
    void testUnlockAccount_SetsActiveAndClearsLock() {
        Role role = new Role();
        role.setId(2L);
        role.setCode("LIBRARIAN");
        role.setName("Thủ thư");

        User user = new User();
        user.setId(60L);
        user.setRole(role);
        user.setEmail("user60@libra.edu.vn");
        user.setStatus("LOCKED");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        user.setTokenVersion(3);

        when(userRepository.findById(60L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AccountResponse response = userManagementService.updateAccountStatus(60L, "ACTIVE", 1L, "127.0.0.1");

        assertEquals("ACTIVE", response.status());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(auditLogService, times(1)).logUserStatusUpdated(eq(1L), eq(60L), eq("LOCKED"), eq("ACTIVE"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Admin không thể tự khóa tài khoản của chính mình")
    void testLockAccount_CannotLockSelf() {
        User admin = new User();
        admin.setId(1L);
        admin.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        ApiException ex = assertThrows(ApiException.class, () ->
                userManagementService.updateAccountStatus(1L, "LOCKED", 1L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CANNOT_LOCK_SELF", ex.getCode());
        verify(userRepository, never()).save(any(User.class));
    }
}
