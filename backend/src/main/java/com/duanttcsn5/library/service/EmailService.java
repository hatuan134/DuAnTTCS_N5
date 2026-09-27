package com.duanttcsn5.library.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Executor mailTaskExecutor;
    private final String frontendUrl;
    private final String mailFrom;
    private final int maxAttempts;
    private final long retryDelayMs;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Qualifier("mailTaskExecutor") Executor mailTaskExecutor,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.mail.from:noreply@libra.edu.vn}") String mailFrom,
            @Value("${app.mail.max-attempts:3}") int maxAttempts,
            @Value("${app.mail.retry-delay-ms:1500}") long retryDelayMs) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailTaskExecutor = mailTaskExecutor;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
    }

    /**
     * Schedule the initial-password email only after the surrounding database
     * transaction commits successfully. The SMTP call runs on a dedicated
     * background executor, so account creation never waits for Gmail.
     */
    public void sendInitialPasswordEmail(String toEmail, String fullName, String rawToken) {
        Runnable enqueueMail = () -> submitMailTask(toEmail, fullName, rawToken);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueMail.run();
                }
            });
            log.info("Queued initial-password email for {} after transaction commit", toEmail);
            return;
        }

        // Safe fallback for callers that are not inside a transaction.
        enqueueMail.run();
    }

    private void submitMailTask(String toEmail, String fullName, String rawToken) {
        try {
            mailTaskExecutor.execute(() -> sendWithRetry(toEmail, fullName, rawToken));
        } catch (RejectedExecutionException exception) {
            log.error("Mail queue is full; could not queue initial-password email for {}", toEmail, exception);
        }
    }

    private void sendWithRetry(String toEmail, String fullName, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.error("JavaMailSender is unavailable; initial-password email was not sent to {}", toEmail);
            return;
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sendNow(mailSender, toEmail, fullName, rawToken);
                log.info("Initial-password email sent successfully to {}", toEmail);
                return;
            } catch (Exception exception) {
                if (attempt >= maxAttempts) {
                    log.error(
                            "Initial-password email failed for {} after {} attempt(s)",
                            toEmail,
                            maxAttempts,
                            exception);
                    return;
                }

                log.warn(
                        "Initial-password email attempt {}/{} failed for {}. Retrying...",
                        attempt,
                        maxAttempts,
                        toEmail);

                if (!sleepBeforeRetry()) {
                    log.warn("Mail retry interrupted for {}", toEmail);
                    return;
                }
            }
        }
    }

    private void sendNow(JavaMailSender mailSender, String toEmail, String fullName, String rawToken)
            throws Exception {
        String setupLink = frontendUrl + "/set-initial-password?token=" + rawToken;

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
    }

    private boolean sleepBeforeRetry() {
        if (retryDelayMs <= 0) {
            return true;
        }

        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
