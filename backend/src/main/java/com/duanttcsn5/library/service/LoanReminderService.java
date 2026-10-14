package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderGroupDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderItemDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LoanReminderService {

    private static final Logger log = LoggerFactory.getLogger(LoanReminderService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final LoanReminderRepository repository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public LoanReminderService(LoanReminderRepository repository, EmailService emailService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    public LoanReminderPreviewResponse getPreview(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(VN_ZONE);
        List<LoanReminderGroupDto> groups = buildReminderGroups(targetDate);
        int totalItems = groups.stream().mapToInt(g -> g.items().size()).sum();
        return new LoanReminderPreviewResponse(targetDate, groups.size(), totalItems, groups);
    }

    @Transactional
    public LoanReminderExecutionResult processDailyReminders(
            LocalDate date,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now(VN_ZONE);
        List<LoanReminderGroupDto> groups = buildReminderGroups(targetDate);

        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int totalItemsCount = 0;

        for (LoanReminderGroupDto group : groups) {
            totalItemsCount += group.items().size();

            // 1. Prevent duplicate sending on the same day for the same group
            if (repository.isAlreadySentToday(targetDate, group.readerId(), group.reminderGroup())) {
                log.info("Reminder already sent today for readerId={} group={}; skipping", group.readerId(), group.reminderGroup());
                skippedCount++;
                continue;
            }

            // 2. Validate email
            String email = group.recipientEmail() != null ? group.recipientEmail().trim() : "";
            if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
                log.warn("Invalid or missing email for readerId={}: '{}'", group.readerId(), email);
                recordLog(targetDate, group, "SKIPPED", "Địa chỉ email không hợp lệ hoặc bị trống",
                        triggeredBy, triggeredByUserId, triggeredByUserName);
                skippedCount++;
                continue;
            }

            // 3. Attempt email dispatch
            try {
                emailService.sendRawHtmlEmail(email, group.subject(), group.emailContentHtml());
                recordLog(targetDate, group, "SENT", null,
                        triggeredBy, triggeredByUserId, triggeredByUserName);
                sentCount++;
                log.info("Sent reminder email to {} for group {}", email, group.reminderGroup());
            } catch (Exception ex) {
                log.error("Failed to send reminder email to {} for group {}: {}", email, group.reminderGroup(), ex.getMessage(), ex);
                recordLog(targetDate, group, "FAILED", ex.getMessage(),
                        triggeredBy, triggeredByUserId, triggeredByUserName);
                failedCount++;
            }
        }

        return new LoanReminderExecutionResult(
                targetDate,
                groups.size(),
                sentCount,
                failedCount,
                skippedCount,
                totalItemsCount,
                triggeredBy
        );
    }

    public List<LoanReminderLogResponse> getLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            int page,
            int size
    ) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return repository.findLogs(date, reminderGroup, status, offset, size);
    }

    public long countLogs(LocalDate date, String reminderGroup, String status) {
        return repository.countLogs(date, reminderGroup, status);
    }

    private List<LoanReminderGroupDto> buildReminderGroups(LocalDate targetDate) {
        List<EligibleLoanItemRow> items = repository.findEligibleItemsForDate(targetDate);
        LocalDate in2Days = targetDate.plusDays(2);

        // Group key: readerId + ":" + reminderGroup
        Map<String, List<EligibleLoanItemRow>> grouped = new LinkedHashMap<>();

        for (EligibleLoanItemRow item : items) {
            LocalDate dueLocalDate = item.dueDate().atZoneSameInstant(VN_ZONE).toLocalDate();
            String groupKey;
            if (dueLocalDate.equals(in2Days)) {
                groupKey = item.readerId() + ":DUE_IN_2_DAYS";
            } else if (dueLocalDate.equals(targetDate)) {
                groupKey = item.readerId() + ":DUE_TODAY";
            } else {
                continue;
            }

            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(item);
        }

        List<LoanReminderGroupDto> result = new ArrayList<>();

        for (Map.Entry<String, List<EligibleLoanItemRow>> entry : grouped.entrySet()) {
            List<EligibleLoanItemRow> groupItems = entry.getValue();
            EligibleLoanItemRow first = groupItems.get(0);
            String groupType = entry.getKey().split(":")[1];

            List<LoanReminderItemDto> itemDtos = groupItems.stream().map(i -> new LoanReminderItemDto(
                    i.itemId(),
                    i.loanId(),
                    i.loanNumber(),
                    i.bookId(),
                    i.bookTitle(),
                    i.copyBarcode(),
                    i.dueDate(),
                    0L,
                    0L
            )).toList();

            boolean alreadySent = repository.isAlreadySentToday(targetDate, first.readerId(), groupType);
            String subject = buildSubject(groupType);
            String htmlContent = buildEmailHtml(first.readerName(), groupType, targetDate, itemDtos);

            result.add(new LoanReminderGroupDto(
                    first.readerId(),
                    first.readerName(),
                    first.readerEmail(),
                    groupType,
                    itemDtos,
                    subject,
                    htmlContent,
                    alreadySent
            ));
        }

        return result;
    }

    private String buildSubject(String groupType) {
        if ("DUE_IN_2_DAYS".equals(groupType)) {
            return "[Thư viện Libra] Nhắc nhở: Sách mượn sắp đến hạn trả (còn 2 ngày)";
        }
        return "[Thư viện Libra] Thông báo: Sách mượn đến hạn trả hôm nay";
    }

    private String buildEmailHtml(
            String readerName,
            String groupType,
            LocalDate targetDate,
            List<LoanReminderItemDto> items
    ) {
        boolean isDueIn2Days = "DUE_IN_2_DAYS".equals(groupType);
        String formattedDate = targetDate.format(DATE_FORMATTER);
        String formattedIn2Days = targetDate.plusDays(2).format(DATE_FORMATTER);

        String title = isDueIn2Days ? "Sách mượn sắp đến hạn trả (còn 2 ngày)" : "Sách mượn đến hạn trả hôm nay";
        String alertMessage = isDueIn2Days
                ? String.format("Bạn có <strong>%d</strong> cuốn sách sắp đến hạn trả vào ngày <strong>%s</strong> (sau 2 ngày nữa). Vui lòng sắp xếp mang sách tới thư viện trả đúng hạn để tránh phát sinh phí trễ hạn.", items.size(), formattedIn2Days)
                : String.format("Bạn có <strong>%d</strong> cuốn sách đến hạn trả <strong>hôm nay</strong> (ngày <strong>%s</strong>). Vui lòng mang sách tới quầy thư viện trả trong ngày hôm nay.", items.size(), formattedDate);

        String statusBadgeText = isDueIn2Days ? "Còn 2 ngày" : "Đến hạn hôm nay";
        String statusColor = isDueIn2Days ? "#2563eb" : "#d97706";

        StringBuilder rows = new StringBuilder();
        int idx = 1;
        for (LoanReminderItemDto item : items) {
            String dueStr = item.dueDate() != null
                    ? item.dueDate().atZoneSameInstant(VN_ZONE).format(DATE_FORMATTER)
                    : "";
            rows.append(String.format("""
                <tr style="border-bottom: 1px solid #e2e8f0;">
                    <td style="padding: 10px; text-align: center;">%d</td>
                    <td style="padding: 10px; font-weight: 500;">%s</td>
                    <td style="padding: 10px;">%s</td>
                    <td style="padding: 10px; text-align: center; color: #475569;">%s</td>
                    <td style="padding: 10px; text-align: center; font-weight: 600; color: #2563eb;">%s</td>
                    <td style="padding: 10px; text-align: center; font-weight: 600; color: %s;">%s</td>
                    <td style="padding: 10px; text-align: right; font-weight: 500; color: #16a34a;">0 đ (Trong hạn)</td>
                </tr>
                """, idx++, escapeHtml(item.loanNumber()), escapeHtml(item.bookTitle()),
                    escapeHtml(item.copyBarcode()), dueStr, statusColor, statusBadgeText));
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1e293b; background-color: #f8fafc; margin: 0; padding: 20px; }
                    .card { max-width: 680px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }
                    .header { background: linear-gradient(135deg, #1e3a8a 0%%, #2563eb 100%%); color: #ffffff; padding: 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: -0.02em; }
                    .body { padding: 28px 24px; }
                    .alert-box { background-color: %s; border-left: 4px solid %s; padding: 14px 18px; border-radius: 6px; margin-bottom: 24px; font-size: 14px; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 16px; font-size: 13px; }
                    th { background-color: #f1f5f9; color: #475569; padding: 10px; text-align: left; font-weight: 600; }
                    .footer { background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px; text-align: center; font-size: 12px; color: #64748b; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>THƯ VIỆN LIBRA</h1>
                        <p style="margin: 4px 0 0 0; opacity: 0.9; font-size: 14px;">%s</p>
                    </div>
                    <div class="body">
                        <p>Kính gửi bạn đọc <strong>%s</strong>,</p>
                        <div class="alert-box">
                            %s
                        </div>
                        <p style="font-weight: 600; margin-bottom: 8px;">Danh sách sách mượn liên quan:</p>
                        <table>
                            <thead>
                                <tr>
                                    <th style="text-align: center; width: 40px;">STT</th>
                                    <th>Mã phiếu</th>
                                    <th>Tên sách</th>
                                    <th style="text-align: center;">Mã vạch</th>
                                    <th style="text-align: center;">Hạn trả</th>
                                    <th style="text-align: center;">Tình trạng</th>
                                    <th style="text-align: right;">Phí phạt phát sinh</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>
                        <p style="margin-top: 24px; font-size: 13px; color: #64748b;">
                            * Lưu ý: Toàn bộ sách trên đều đang trong thời hạn mượn hợp lệ và chưa phát sinh bất kỳ khoản phí phạt nào. Bạn đọc hãy mang sách tới trả đúng hạn để bảo đảm quyền lợi mượn sách.
                        </p>
                    </div>
                    <div class="footer">
                        <p style="margin: 0;">Email này được gửi tự động từ Hệ thống Quản lý Thư viện Libra.</p>
                        <p style="margin: 4px 0 0 0;">Nếu cần hỗ trợ thêm, bạn đọc vui lòng liên hệ trực tiếp quầy thủ thư thư viện.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                isDueIn2Days ? "#eff6ff" : "#fef3c7",
                isDueIn2Days ? "#3b82f6" : "#f59e0b",
                title,
                escapeHtml(readerName),
                alertMessage,
                rows.toString()
        );
    }

    private void recordLog(
            LocalDate targetDate,
            LoanReminderGroupDto group,
            String status,
            String errorMessage,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName
    ) {
        String loanNumbers = group.items().stream().map(LoanReminderItemDto::loanNumber).distinct().collect(Collectors.joining(", "));
        String bookTitles = group.items().stream().map(LoanReminderItemDto::bookTitle).distinct().collect(Collectors.joining("; "));
        String itemsDetailJson;
        try {
            itemsDetailJson = objectMapper.writeValueAsString(group.items());
        } catch (Exception e) {
            itemsDetailJson = "[]";
        }

        repository.insertLog(
                targetDate,
                group.readerId(),
                group.readerName(),
                group.recipientEmail(),
                group.reminderGroup(),
                group.items().size(),
                loanNumbers,
                bookTitles,
                itemsDetailJson,
                group.subject(),
                group.emailContentHtml(),
                status,
                errorMessage,
                triggeredBy != null ? triggeredBy : "SYSTEM_SCHEDULED",
                triggeredByUserId,
                triggeredByUserName,
                false
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
