package com.duanttcsn5.library.service;

import jakarta.mail.MessagingException;
import org.springframework.mail.MailSendException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** SMTP diagnostics stay in server logs; the reminder UI receives Vietnamese guidance. */
public final class LoanReminderFailureMessages {
    private LoanReminderFailureMessages() {}

    public static String from(Throwable failure) {
        StringBuilder details = new StringBuilder();
        collect(failure, Collections.newSetFromMap(new IdentityHashMap<>()), details);
        return message(details.toString());
    }

    private static void collect(Throwable failure, Set<Throwable> seen, StringBuilder details) {
        if (failure == null || seen.size() >= 32 || !seen.add(failure)) return;
        details.append(' ').append(failure.getClass().getSimpleName()).append(' ').append(failure.getMessage());
        collect(failure.getCause(), seen, details);
        if (failure instanceof MessagingException mail) collect(mail.getNextException(), seen, details);
        if (failure instanceof MailSendException mail)
            mail.getFailedMessages().values().forEach(error -> collect(error, seen, details));
    }

    /** Also localizes diagnostics already recorded before S4-00.1, without editing audit rows. */
    public static String message(String raw) {
        if (raw == null || raw.isBlank()) return null;
        switch (raw) {
            case "SMTP_AUTH": return "Xác thực SMTP thất bại. Cần kiểm tra cấu hình trước khi xử lý tiếp.";
            case "SMTP_CONFIG": return "Thiếu hoặc sai cấu hình gửi thư. Email chưa được gửi.";
            case "SMTP_CONNECT": return "Không kết nối được SMTP trước khi gửi thư; đang chờ thử lại.";
            case "SMTP_REJECTED": return "SMTP từ chối người nhận. Cần kiểm tra địa chỉ email.";
            case "SMTP_TEMPORARY_REJECTION": return "SMTP tạm thời từ chối thư; đang chờ thử lại.";
            case "SMTP_UNKNOWN": return "Chưa xác định SMTP đã nhận thư hay chưa. Cần đối soát; không tự động gửi lại.";
            case "DB_AFTER_SMTP": return "SMTP đã chấp nhận thư nhưng lỗi lưu kết quả. Cần đối soát; không gửi lại.";
            case "DB_RESULT_UNKNOWN": return "Lỗi lưu kết quả xử lý. Cần đối soát trước khi gửi lại.";
            case "NO_LONGER_ELIGIBLE": return "Bỏ qua bản nhắc cũ: đã qua ngày nhắc hoặc thông tin mượn trả đã thay đổi.";
            case "SIMULATION": return "Mô phỏng lỗi; không gửi SMTP và không đưa vào hàng đợi gửi thật.";
            default: break;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("5.1.1") || value.contains("user unknown") || value.contains("no such user")
                || value.contains("does not exist") || value.contains("recipient not found")
                || value.contains("address not found") || value.contains("mailbox not found"))
            return "Không gửi được email nhắc hạn vì địa chỉ email người nhận không tồn tại hoặc không được máy chủ chấp nhận. Vui lòng kiểm tra email của bạn đọc.";
        if (value.contains("authentication") || value.contains("535") || value.contains("bad credentials"))
            return "Không gửi được email nhắc hạn vì tài khoản gửi thư chưa xác thực thành công. Vui lòng kiểm tra cấu hình email.";
        if (value.contains("timed out") || value.contains("timeout") || value.contains("connect")
                || value.contains("unknownhost") || value.contains("connection"))
            return "Không kết nối được máy chủ gửi email hoặc kết nối đã hết thời gian chờ. Vui lòng kiểm tra mạng và cấu hình email.";
        if (value.contains("5.2.2") || value.contains("mailbox full") || value.contains("quota exceeded"))
            return "Không gửi được email nhắc hạn vì hộp thư của bạn đọc đã đầy. Vui lòng liên hệ bạn đọc để kiểm tra.";
        if (value.contains("550") || value.contains("recipient address rejected") || value.contains("invalid address")
                || value.contains("mailbox unavailable") || value.contains("addressfailed"))
            return "Máy chủ đã từ chối địa chỉ email người nhận. Vui lòng kiểm tra email của bạn đọc.";
        if (raw.startsWith("Mô phỏng") || raw.startsWith("Địa chỉ email không hợp lệ")
                || raw.startsWith("Không gửi được email nhắc hạn") || raw.startsWith("Không kết nối được máy chủ gửi email")
                || raw.startsWith("Máy chủ đã từ chối địa chỉ email người nhận")) return raw;
        return "Chưa gửi được email nhắc hạn. Vui lòng kiểm tra địa chỉ email của bạn đọc và cấu hình gửi thư trước khi thử lại.";
    }
}
