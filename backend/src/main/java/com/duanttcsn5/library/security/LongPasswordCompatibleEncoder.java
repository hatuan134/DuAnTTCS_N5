package com.duanttcsn5.library.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Giữ khả năng đăng nhập bằng hash BCrypt cũ, đồng thời bảo vệ mật khẩu
 * dài hơn 72 byte UTF-8 khỏi việc BCrypt âm thầm bỏ qua phần phía sau.
 */
public final class LongPasswordCompatibleEncoder implements PasswordEncoder {
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String LONG_PASSWORD_PREFIX = "{bcrypt-sha256}";

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Mật khẩu không được để trống");
        }
        String password = rawPassword.toString();
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return LONG_PASSWORD_PREFIX + bcrypt.encode(hashLongPassword(password));
        }
        // Giữ định dạng hash BCrypt cũ cho mật khẩu bình thường.
        return bcrypt.encode(password);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith(LONG_PASSWORD_PREFIX)) {
            return bcrypt.matches(
                    hashLongPassword(rawPassword.toString()),
                    encodedPassword.substring(LONG_PASSWORD_PREFIX.length()));
        }
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith(LONG_PASSWORD_PREFIX)) {
            return bcrypt.upgradeEncoding(encodedPassword.substring(LONG_PASSWORD_PREFIX.length()));
        }
        return bcrypt.upgradeEncoding(encodedPassword);
    }

    private static String hashLongPassword(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không hỗ trợ SHA-256", e);
        }
    }
}
