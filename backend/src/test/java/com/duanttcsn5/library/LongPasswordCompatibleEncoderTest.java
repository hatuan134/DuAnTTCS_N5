package com.duanttcsn5.library;

import com.duanttcsn5.library.security.LongPasswordCompatibleEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class LongPasswordCompatibleEncoderTest {
    private final PasswordEncoder encoder = new LongPasswordCompatibleEncoder();

    @Test
    void normalPasswordsUseLegacyBcryptAndRemainCompatible() {
        String password = "Abc12345";
        String oldHash = new BCryptPasswordEncoder().encode(password);
        assertThat(encoder.matches(password, oldHash)).isTrue();
        assertThat(encoder.matches("wrong123", oldHash)).isFalse();

        String newHash = encoder.encode(password);
        assertThat(newHash).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(password, newHash)).isTrue();
    }

    @Test
    void longPasswordsDoNotGetTruncatedAtBcrypt72ByteLimit() {
        String password = "Abc12345" + "x".repeat(80);
        String hash = encoder.encode(password);
        assertThat(hash).startsWith("{bcrypt-sha256}");
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password.substring(0, 80) + "different", hash)).isFalse();
        assertThat(encoder.matches(password.substring(0, 72), hash)).isFalse();
    }

    @Test
    void unicodePasswordsCountActualUtf8Bytes() {
        String password = "Mậtkhẩu123" + "ấ".repeat(50);
        String hash = encoder.encode(password);
        assertThat(hash).startsWith("{bcrypt-sha256}");
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password + "!", hash)).isFalse();
    }
}
