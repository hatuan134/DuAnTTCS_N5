package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String frontendUrl;
    private final String mailFrom;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.mail.from:noreply@libra.edu.vn}") String mailFrom) {
        this.mailSenderProvider = mailSenderProvider;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
    }

    public void sendInitialPasswordEmail(String toEmail, String fullName, String rawToken) {
        String setupLink = frontendUrl + "/set-initial-password?token=" + rawToken;

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw emailUnavailable();
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom, "Thư viện LIBRA");
            helper.setTo(toEmail);
            helper.setSubject("Thiết lập mật khẩu tài khoản Thư viện LIBRA");

            String html = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1e293b; line-height: 1.6; }
                            .container { max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px; }
                            .header { text-align: center; margin-bottom: 24px; }
                            .logo { font-size: 24px; font-weight: bold; color: #2563eb; }
                            .btn { display: inline-block; background-color: #2563eb; color: #ffffff !important; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 500; margin: 20px 0; }
                            .footer { margin-top: 30px; font-size: 13px; color: #64748b; border-top: 1px solid #e2e8f0; padding-top: 16px; }
                            .warning { color: #dc2626; font-size: 13px; margin-top: 8px; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <div class="logo">LIBRA Library Management</div>
                            </div>
                            <p>Kính gửi <strong>%s</strong>,</p>
                            <p>Tài khoản nhân viên của bạn tại Hệ thống Quản lý Thư viện LIBRA đã được Quản trị viên khởi tạo thành công.</p>
                            <p>Để hoàn tất quá trình kích hoạt tài khoản và bắt đầu sử dụng hệ thống, vui lòng nhấn vào nút bên dưới để thiết lập mật khẩu truy cập của bạn:</p>
                            <div style="text-align: center;">
                                <a href="%s" class="btn">Thiết lập mật khẩu lần đầu</a>
                            </div>
                            <p>Hoặc truy cập trực tiếp bằng đường link sau:</p>
                            <p style="word-break: break-all;"><a href="%s">%s</a></p>
                            <p class="warning">⚠️ Lưu ý: Đường dẫn này chỉ có hiệu lực trong vòng <strong>24 giờ</strong> và chỉ được sử dụng duy nhất một lần. Vì lý do bảo mật, tuyệt đối không chia sẻ liên kết này cho bất kỳ ai.</p>
                            <div class="footer">
                                <p>Email này được gửi tự động từ Hệ thống Thư viện LIBRA. Vui lòng không trả lời thư này.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(fullName, setupLink, setupLink, setupLink);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Đã gửi email thiết lập mật khẩu lần đầu tới {}", toEmail);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Không thể gửi email thiết lập mật khẩu tới {}", toEmail, exception);
            throw emailUnavailable();
        }
    }

    private ApiException emailUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_SEND_FAILED",
                "Không thể gửi email thiết lập mật khẩu. Vui lòng kiểm tra cấu hình email và thử lại.");
    }
}
