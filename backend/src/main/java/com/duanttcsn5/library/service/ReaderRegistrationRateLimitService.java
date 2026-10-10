package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S1-03: Giới hạn tối đa 3 yêu cầu đăng ký hợp lệ về cấu trúc từ một IP
 * trong khoảng thời gian trượt 60 phút, kể cả yêu cầu bị từ chối do trùng email.
 * Lưu lần thử trong PostgreSQL và khóa giao dịch theo IP để tránh đua đồng thời.
 */
@Service
public class ReaderRegistrationRateLimitService {
    private static final long MAX_ATTEMPTS_PER_HOUR = 3;
    private final JdbcTemplate jdbcTemplate;

    public ReaderRegistrationRateLimitService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Giao dịch riêng: vẫn ghi nhận lượt gửi khi nghiệp vụ đăng ký bị rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecord(String ipAddress) {
        String ip = (ipAddress == null || ipAddress.isBlank()) ? "unknown" : ipAddress.trim();

        // PostgreSQL advisory lock bảo đảm 2 request cùng IP không đồng thời vượt giới hạn.
        // getRemoteAddr() là nguồn IP; KHÔNG tin X-Forwarded-For do client tự gửi.
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { /* Chỉ cần chờ đến khi lấy được khóa. */ },
                "reader-registration:" + ip
        );

        Long recent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reader_registration_attempts "
                        + "WHERE ip_address = ? AND attempted_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                Long.class,
                ip
        );
        if (recent != null && recent >= MAX_ATTEMPTS_PER_HOUR) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "REGISTRATION_RATE_LIMITED",
                    "Mỗi địa chỉ IP chỉ được gửi tối đa 3 lượt đăng ký trong 1 giờ. "
                            + "Vui lòng thử lại sau khi lượt đăng ký cũ hết hạn."
            );
        }

        jdbcTemplate.update(
                "INSERT INTO reader_registration_attempts (ip_address) VALUES (?)",
                ip
        );
    }

    // Dọn IP/lượt thử hết hiệu lực; công việc lịch trình đã được bật sẵn trong project.
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void deleteExpiredAttempts() {
        jdbcTemplate.update(
                "DELETE FROM reader_registration_attempts "
                        + "WHERE attempted_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'"
        );
    }
}
