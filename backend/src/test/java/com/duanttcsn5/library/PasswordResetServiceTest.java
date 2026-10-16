package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.auth.ForgotPasswordRequest;
import com.duanttcsn5.library.dto.auth.ForgotPasswordResponse;
import com.duanttcsn5.library.dto.auth.ResetPasswordRequest;
import com.duanttcsn5.library.dto.auth.ResetPasswordResponse;
import com.duanttcsn5.library.dto.auth.ValidateResetPasswordTokenResponse;
import com.duanttcsn5.library.entity.PasswordResetRequest;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PasswordResetRequestRepository;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.AuditLogService;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.PasswordResetService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetRequestRepository passwordResetRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                passwordResetRequestRepository,
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService,
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
    @DisplayName("S1-07: Yêu cầu đặt lại mật khẩu với email tồn tại => Sinh token và gửi email")
    void testRequestPasswordReset_Success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("reader@libra.edu.vn");
        user.setFullName("Nguyen Van A");
        user.setStatus("ACTIVE");

        when(passwordResetRequestRepository.countByRequestedEmailIgnoreCaseAndRequestTypeAndCreatedAtAfter(
                eq("reader@libra.edu.vn"), eq("RESET_PASSWORD"), any())).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("reader@libra.edu.vn")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(
                new ForgotPasswordRequest("reader@libra.edu.vn"), "127.0.0.1");

        assertNotNull(response);
        assertTrue(response.message().contains("hướng dẫn đặt lại mật khẩu"));

        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(passwordResetRequestRepository).save(captor.capture());
        PasswordResetRequest saved = captor.getValue();
        assertEquals("reader@libra.edu.vn", saved.getRequestedEmail());
        assertEquals("RESET_PASSWORD", saved.getRequestType());
        assertNotNull(saved.getTokenHash());
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));

        verify(emailService).sendResetPasswordEmail(eq("reader@libra.edu.vn"), eq("Nguyen Van A"), anyString());
        verify(auditLogService).logPasswordResetRequested(eq(1L), eq("reader@libra.edu.vn"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("S1-07 AC2: Email không tồn tại => Vẫn trả về thông báo trung tính, không gửi email")
    void testRequestPasswordReset_UnknownEmail_ReturnsNeutralMessage() {
        when(passwordResetRequestRepository.countByRequestedEmailIgnoreCaseAndRequestTypeAndCreatedAtAfter(
                eq("unknown@libra.edu.vn"), eq("RESET_PASSWORD"), any())).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("unknown@libra.edu.vn")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(
                new ForgotPasswordRequest("unknown@libra.edu.vn"), "127.0.0.1");

        assertNotNull(response);
        assertTrue(response.message().contains("hướng dẫn đặt lại mật khẩu"));

        verify(passwordResetRequestRepository, never()).save(any());
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("S1-07 AC3: Rate limit quá 3 lần/giờ => Chặn gửi email, vẫn trả thông báo trung tính")
    void testRequestPasswordReset_RateLimitExceeded() {
        when(passwordResetRequestRepository.countByRequestedEmailIgnoreCaseAndRequestTypeAndCreatedAtAfter(
                eq("reader@libra.edu.vn"), eq("RESET_PASSWORD"), any())).thenReturn(3L);

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(
                new ForgotPasswordRequest("reader@libra.edu.vn"), "127.0.0.1");

        assertNotNull(response);
        assertTrue(response.message().contains("hướng dẫn đặt lại mật khẩu"));

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("S1-07: Xác thực token hợp lệ còn trong hạn 30 phút")
    void testValidateToken_Success() {
        String rawToken = "my-secret-token-123456";
        String tokenHash = sha256(rawToken);

        User user = new User();
        user.setId(2L);
        user.setEmail("reader@libra.edu.vn");
        user.setFullName("Nguyen Van B");

        PasswordResetRequest req = new PasswordResetRequest();
        req.setUser(user);
        req.setRequestedEmail("reader@libra.edu.vn");
        req.setTokenHash(tokenHash);
        req.setRequestType("RESET_PASSWORD");
        req.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(20));
        req.setUsedAt(null);

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "RESET_PASSWORD"))
                .thenReturn(Optional.of(req));

        ValidateResetPasswordTokenResponse res = passwordResetService.validateToken(rawToken);
        assertTrue(res.valid());
        assertEquals("Nguyen Van B", res.fullName());
        assertNotNull(res.email());
    }

    @Test
    @DisplayName("S1-07: Xác thực token đã hết hạn quá 30 phút => Ném lỗi TOKEN_EXPIRED")
    void testValidateToken_Expired_ThrowsException() {
        String rawToken = "expired-token-123456";
        String tokenHash = sha256(rawToken);

        PasswordResetRequest req = new PasswordResetRequest();
        req.setTokenHash(tokenHash);
        req.setRequestType("RESET_PASSWORD");
        req.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        req.setUsedAt(null);

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "RESET_PASSWORD"))
                .thenReturn(Optional.of(req));

        ApiException ex = assertThrows(ApiException.class, () -> passwordResetService.validateToken(rawToken));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("TOKEN_EXPIRED", ex.getCode());
    }

    @Test
    @DisplayName("S1-07: Xác thực token đã sử dụng => Ném lỗi TOKEN_ALREADY_USED")
    void testValidateToken_AlreadyUsed_ThrowsException() {
        String rawToken = "used-token-123456";
        String tokenHash = sha256(rawToken);

        PasswordResetRequest req = new PasswordResetRequest();
        req.setTokenHash(tokenHash);
        req.setRequestType("RESET_PASSWORD");
        req.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        req.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "RESET_PASSWORD"))
                .thenReturn(Optional.of(req));

        ApiException ex = assertThrows(ApiException.class, () -> passwordResetService.validateToken(rawToken));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("TOKEN_ALREADY_USED", ex.getCode());
    }

    @Test
    @DisplayName("S1-07 AC4: Đổi mật khẩu thành công => Tăng token_version và thu hồi toàn bộ refresh token")
    void testResetPassword_Success() {
        String rawToken = "valid-reset-token-789";
        String tokenHash = sha256(rawToken);

        User user = new User();
        user.setId(5L);
        user.setEmail("user5@libra.edu.vn");
        user.setPasswordHash("old-hash");
        user.setTokenVersion(1);

        PasswordResetRequest req = new PasswordResetRequest();
        req.setUser(user);
        req.setRequestedEmail("user5@libra.edu.vn");
        req.setTokenHash(tokenHash);
        req.setRequestType("RESET_PASSWORD");
        req.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        req.setUsedAt(null);

        when(passwordResetRequestRepository.findByTokenHashAndRequestTypeWithUser(tokenHash, "RESET_PASSWORD"))
                .thenReturn(Optional.of(req));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded-hash");

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");
        ResetPasswordResponse response = passwordResetService.resetPassword(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("new-encoded-hash", user.getPasswordHash());
        assertEquals(2, user.getTokenVersion(), "tokenVersion phải được tăng lên để vô hiệu hóa token cũ");
        assertNotNull(req.getUsedAt(), "Token phải được đánh dấu đã sử dụng");

        verify(userRepository).save(user);
        verify(passwordResetRequestRepository).save(req);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(5L), any());
        verify(auditLogService).logPasswordResetCompleted(eq(5L), eq("user5@libra.edu.vn"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("S1-07: Mật khẩu xác nhận không khớp => Ném lỗi PASSWORDS_DO_NOT_MATCH")
    void testResetPassword_MismatchConfirmPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "NewPassword123", "DifferentPassword123");
        ApiException ex = assertThrows(ApiException.class, () -> passwordResetService.resetPassword(request, "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("PASSWORDS_DO_NOT_MATCH", ex.getCode());
    }
}
