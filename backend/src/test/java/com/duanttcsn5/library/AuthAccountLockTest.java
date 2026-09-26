package com.duanttcsn5.library;

import com.duanttcsn5.library.config.AuthProperties;
import com.duanttcsn5.library.dto.auth.LoginRequest;
import com.duanttcsn5.library.entity.RefreshToken;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.RefreshTokenRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.security.JwtService;
import com.duanttcsn5.library.service.AuditLogService;
import com.duanttcsn5.library.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAccountLockTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditLogService auditLogService;

    private AuthService authService;
    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setMaxFailedAttempts(5);
        authProperties.setLockMinutes(15);
        authProperties.setRefreshTokenDays(7);

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                authProperties,
                auditLogService);
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
    @DisplayName("CASE 11: Tài khoản bị khóa (status=LOCKED) => Đăng nhập bị từ chối")
    void testLogin_LockedAccount_ThrowsInvalidCredentials() {
        User user = new User();
        user.setId(10L);
        user.setEmail("locked@libra.edu.vn");
        user.setPasswordHash("encoded-pwd");
        user.setStatus("LOCKED"); // Bị khóa

        when(userRepository.findForLogin("locked@libra.edu.vn")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("locked@libra.edu.vn", "Password@123");
        ApiException ex = assertThrows(ApiException.class, () ->
                authService.login(request, "127.0.0.1"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("CASE 13: Refresh token của tài khoản đã bị khóa => Không cấp access token mới")
    void testRefresh_LockedAccount_ThrowsInvalidRefreshToken() {
        String rawRefreshToken = "some-refresh-token-value";
        String tokenHash = sha256(rawRefreshToken);

        User user = new User();
        user.setId(10L);
        user.setStatus("LOCKED"); // Bị khóa

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5));
        rt.setRevokedAt(null);

        when(refreshTokenRepository.findByTokenHashWithUser(tokenHash)).thenReturn(Optional.of(rt));

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.refresh(rawRefreshToken));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_REFRESH_TOKEN", ex.getCode());
        verify(jwtService, never()).issueAccessToken(any());
    }
}
