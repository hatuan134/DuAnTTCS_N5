package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderGroupDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderItemDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderRetryExecutionResult;
import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final LibraryConfigurationService libraryConfigurationService;

    public LoanReminderService(LoanReminderRepository repository, EmailService emailService, ObjectMapper objectMapper) {
        this(repository, emailService, objectMapper, null);
    }

    @Autowired
    public LoanReminderService(
            LoanReminderRepository repository,
            EmailService emailService,
            ObjectMapper objectMapper,
            LibraryConfigurationService libraryConfigurationService
    ) {
        this.repository = repository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.libraryConfigurationService = libraryConfigurationService;
    }

    public LoanReminderPreviewResponse getPreview(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(VN_ZONE);
        List<LoanReminderGroupDto> groups = buildReminderGroups(targetDate);
        int totalItems = groups.stream().mapToInt(g -> g.items().size()).sum();
        long grandTotalFee = groups.stream().mapToLong(LoanReminderGroupDto::totalEstimatedFeeVnd).sum();
        return new LoanReminderPreviewResponse(targetDate, groups.size(), totalItems, grandTotalFee, groups);
    }

    public LoanReminderExecutionResult processDailyReminders(
            LocalDate date,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName
    ) {
        return processDailyReminders(date, triggeredBy, triggeredByUserId, triggeredByUserName, false);
    }

    public LoanReminderExecutionResult processDailyReminders(
            LocalDate date,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName,
            Boolean simulateFailure
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now(VN_ZONE);
        List<LoanReminderGroupDto> groups = buildReminderGroups(targetDate);

        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int totalItemsCount = 0;
        long grandTotalFee = groups.stream().mapToLong(LoanReminderGroupDto::totalEstimatedFeeVnd).sum();

        boolean shouldSimulateFailure = Boolean.TRUE.equals(simulateFailure);

        for (LoanReminderGroupDto group : groups) {
            totalItemsCount += group.items().size();
            if (shouldSimulateFailure) {
                recordInitialLog(targetDate, group, "FAILED", "SIMULATION",
                        triggeredBy, triggeredByUserId, triggeredByUserName, true);
                failedCount++;
                continue;
            }
            Long id;
            try {
                id = repository.claimInitial(targetDate, group, objectMapper.writeValueAsString(group.items()),
                        triggeredBy, triggeredByUserId, triggeredByUserName);
            } catch (Exception ex) {
                log.error("Reminder claim/database failure readerId={} type={}", group.readerId(), ex.getClass().getSimpleName());
                failedCount++;
                continue; // Never contact SMTP without a committed claim.
            }
            if (id == null) { skippedCount++; continue; }
            String email = group.recipientEmail() == null ? "" : group.recipientEmail().trim();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                persistOutcome(id, "SKIPPED", 0, "SMTP_REJECTED", triggeredBy);
                skippedCount++;
                continue;
            }
            String state = deliver(id, email, group.subject(), group.emailContentHtml(), 0, 3, triggeredBy);
            if ("SENT".equals(state)) sentCount++; else failedCount++;
        }

        return new LoanReminderExecutionResult(
                targetDate,
                groups.size(),
                sentCount,
                failedCount,
                skippedCount,
                totalItemsCount,
                grandTotalFee,
                triggeredBy
        );
    }

    /**
     * Process pending retries for failed emails (max 3 retries).
     * Stops immediately upon success or after 3 failed attempts (marking FAILED_MAX_RETRIES).
     * Preserves original book list and estimated fee content.
     */
    public LoanReminderRetryExecutionResult processPendingRetries(
            Long specificLogId,
            Boolean simulateFailure,
            Long triggeredByUserId,
            String triggeredByUserName
    ) {
        List<LoanReminderLogResponse> pendingLogs;
        if (specificLogId != null) {
            var opt = repository.findLogById(specificLogId);
            if (opt.isPresent() && "PENDING_RETRY".equals(opt.get().status())) {
                pendingLogs = List.of(opt.get());
            } else {
                pendingLogs = List.of();
            }
        } else {
            pendingLogs = repository.findPendingRetryLogs();
        }

        int succeededCount = 0, stillPendingCount = 0, maxRetriesReachedCount = 0, processed = 0;
        int reviewCount = 0, permanentCount = 0, skippedCount = 0;
        for (LoanReminderLogResponse item : pendingLogs) {
            // Simulation never consumes a live claim or sends a persisted simulated payload.
            if (Boolean.TRUE.equals(simulateFailure) || item.isDryRun()) continue;
            try {
                if (!repository.claimRetry(item.id(), item.retryCount())) continue;
            } catch (Exception ex) {
                log.error("Reminder retry claim/database failure logId={} type={}", item.id(), ex.getClass().getSimpleName());
                processed++;
                reviewCount++;
                continue;
            }
            processed++;
            boolean eligible;
            try {
                eligible = repository.retryItemsStillEligible(item.id());
            } catch (Exception ex) {
                persistOutcome(item.id(), "REVIEW_REQUIRED", item.retryCount(), "DB_RESULT_UNKNOWN", "RETRY");
                reviewCount++;
                continue;
            }
            if (!eligible) {
                String state = persistOutcome(item.id(), "SKIPPED", item.retryCount(), "NO_LONGER_ELIGIBLE", "RETRY");
                if ("SKIPPED".equals(state)) skippedCount++; else reviewCount++;
                continue;
            }
            String state = deliver(item.id(), item.recipientEmail(), item.subject(), item.emailContent(),
                    item.retryCount() + 1, item.maxRetries(), triggeredByUserId == null ? "AUTO_RETRY" : "MANUAL_RETRY:" + triggeredByUserId);
            if ("SENT".equals(state)) succeededCount++;
            else if ("PENDING_RETRY".equals(state)) stillPendingCount++;
            else if ("FAILED_MAX_RETRIES".equals(state)) maxRetriesReachedCount++;
            else if ("REVIEW_REQUIRED".equals(state)) reviewCount++;
            else permanentCount++;
        }
        return new LoanReminderRetryExecutionResult(processed, succeededCount, stillPendingCount, maxRetriesReachedCount,
                reviewCount, permanentCount, skippedCount);
    }

    private String deliver(Long id, String email, String subject, String html, int retry, int max, String source) {
        String state = "SENT", reason = null;
        try {
            emailService.sendRawHtmlEmail(email, subject, html);
        } catch (Exception ex) {
            var failure = LoanReminderDeliveryFailure.classify(ex);
            state = failure.state();
            reason = failure.code();
            if ("PENDING_RETRY".equals(state) && retry >= max) state = "FAILED_MAX_RETRIES";
            // Class/code only: provider exception text may contain credentials or recipient data.
            log.warn("Reminder transport logId={} code={} type={}", id, reason, ex.getClass().getSimpleName());
        }
        return persistOutcome(id, state, retry, reason, source);
    }

    private String persistOutcome(Long id, String state, int retry, String reason, String source) {
        try {
            repository.finishDelivery(id, state, retry, reason, source);
            return state;
        } catch (Exception ex) {
            log.error("Reminder result persistence failed logId={} smtpOutcome={} type={}", id, state, ex.getClass().getSimpleName());
            try {
                repository.finishDelivery(id, "REVIEW_REQUIRED", retry,
                        "SENT".equals(state) ? "DB_AFTER_SMTP" : "DB_RESULT_UNKNOWN", source);
            } catch (Exception ignored) {
                // The durable PROCESSING claim remains blocked. Never reclaim on a timer.
                log.error("Reminder claim requires reconciliation logId={}", id);
            }
            return "REVIEW_REQUIRED";
        }
    }

    public List<LoanReminderLogResponse> getLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            String search,
            String cardNumber,
            int page,
            int size
    ) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return repository.findLogs(date, reminderGroup, status, search, cardNumber, offset, size);
    }

    public List<LoanReminderLogResponse> getLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            int page,
            int size
    ) {
        return getLogs(date, reminderGroup, status, null, null, page, size);
    }

    public long countLogs(LocalDate date, String reminderGroup, String status, String search, String cardNumber) {
        return repository.countLogs(date, reminderGroup, status, search, cardNumber);
    }

    public long countLogs(LocalDate date, String reminderGroup, String status) {
        return countLogs(date, reminderGroup, status, null, null);
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
            } else if (dueLocalDate.isBefore(targetDate)) {
                groupKey = item.readerId() + ":OVERDUE_DAILY";
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

            List<LoanReminderItemDto> itemDtos = new ArrayList<>();
            for (EligibleLoanItemRow i : groupItems) {
                LocalDate dueLocalDate = i.dueDate().atZoneSameInstant(VN_ZONE).toLocalDate();
                long calendarOverdueDays = 0L;
                long openOverdueDays = 0L;
                long estimatedFee = 0L;

                if (groupType.startsWith("OVERDUE_")) {
                    calendarOverdueDays = Math.max(0L, ChronoUnit.DAYS.between(dueLocalDate, targetDate));
                    openOverdueDays = calendarOverdueDays;

                    if (libraryConfigurationService != null) {
                        try {
                            OffsetDateTime asOf = targetDate.atTime(7, 0).atZone(VN_ZONE).toOffsetDateTime();
                            ReturnFeeAssessment assessment = libraryConfigurationService.previewReturnFee(i.dueDate(), asOf);
                            if (assessment != null) {
                                openOverdueDays = assessment.overdueDays();
                                if (assessment.amountVnd() != null) {
                                    estimatedFee = assessment.amountVnd();
                                }
                            }
                        } catch (Exception ex) {
                            log.warn("Unable to preview return fee for item {}: {}", i.itemId(), ex.getMessage());
                        }
                    }
                }

                itemDtos.add(new LoanReminderItemDto(
                        i.itemId(),
                        i.loanId(),
                        i.loanNumber(),
                        i.bookId(),
                        i.bookTitle(),
                        i.copyBarcode(),
                        i.dueDate(),
                        calendarOverdueDays,
                        openOverdueDays,
                        estimatedFee
                ));
            }

            long totalEstimatedFeeVnd = itemDtos.stream().mapToLong(LoanReminderItemDto::estimatedFeeVnd).sum();
            String deliveryState = repository.deliveryState(targetDate, first.readerId(), groupType);
            if (deliveryState == null) deliveryState = "READY";
            boolean alreadySent = !"READY".equals(deliveryState);
            if (!alreadySent && (first.readerEmail() == null || !EMAIL_PATTERN.matcher(first.readerEmail().trim()).matches()))
                deliveryState = "INVALID_EMAIL";
            String subject = buildSubject(groupType);
            String htmlContent = buildEmailHtml(first.readerName(), groupType, targetDate, itemDtos, totalEstimatedFeeVnd);

            result.add(new LoanReminderGroupDto(
                    first.readerId(),
                    first.readerName(),
                    first.readerCardNumber(),
                    first.readerEmail(),
                    groupType,
                    itemDtos,
                    totalEstimatedFeeVnd,
                    subject,
                    htmlContent,
                    alreadySent,
                    deliveryState
            ));
        }

        return result;
    }

    private String buildSubject(String groupType) {
        return switch (groupType) {
            case "OVERDUE_DAILY" -> "[Thư viện Libra] Nhắc trả sách quá hạn hằng ngày";
            case "DUE_IN_2_DAYS" -> "[Thư viện Libra] Nhắc nhở: Sách mượn sắp đến hạn trả (còn 2 ngày)";
            case "DUE_TODAY" -> "[Thư viện Libra] Thông báo: Sách mượn đến hạn trả hôm nay";
            case "OVERDUE_1_DAY" -> "[Thư viện Libra] Cảnh báo: Sách mượn đã quá hạn 1 ngày";
            case "OVERDUE_7_DAYS" -> "[Thư viện Libra] Cảnh báo: Sách mượn đã quá hạn 7 ngày - Đang phát sinh phí phạt";
            case "OVERDUE_30_DAYS" -> "[Thư viện Libra] Cảnh báo nghiêm trọng: Sách mượn đã quá hạn 30 ngày";
            default -> "[Thư viện Libra] Thông báo mượn trả sách";
        };
    }

    private String buildEmailHtml(String readerName, String groupType, LocalDate targetDate,
            List<LoanReminderItemDto> items, long totalEstimatedFeeVnd) {
        boolean overdue = groupType.startsWith("OVERDUE_");
        String title = overdue ? "Nhắc trả sách quá hạn hằng ngày"
                : "DUE_TODAY".equals(groupType) ? "Đến hạn hôm nay" : "Còn 2 ngày đến hạn trả";
        StringBuilder cards = new StringBuilder();
        for (LoanReminderItemDto item : items) {
            String status = overdue ? "Quá hạn " + item.daysOverdue() + " ngày"
                    : "DUE_TODAY".equals(groupType) ? "Đến hạn hôm nay" : "Còn 2 ngày";
            cards.append("""
                <tr><td style="padding:0 16px 16px;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                    style="width:100%%;table-layout:fixed;border:1px solid #e2e8f0;border-radius:8px;background-color:#ffffff;">
                    <tr><td style="padding:16px;overflow-wrap:anywhere;word-wrap:break-word;word-break:break-word;">
                      <h2 style="margin:0 0 12px;font-size:17px;line-height:1.5;color:#1e3a8a;">%s</h2>
                      <p style="margin:6px 0;color:#475569;">Mã phiếu mượn<br><strong style="color:#1e293b;word-break:break-all;">%s</strong></p>
                      <p style="margin:6px 0;color:#475569;">Mã vạch bản sao<br><strong style="color:#1e293b;word-break:break-all;">%s</strong></p>
                      <p style="margin:6px 0;">Ngày hạn trả: <strong>%s</strong></p>
                      <p style="margin:6px 0;color:%s;"><strong>%s</strong></p>
                      <p style="margin:6px 0;">Số ngày quá hạn: <strong>%d</strong></p>
                      <p style="margin:6px 0;">Phí trễ hạn tạm tính: <strong>%s</strong></p>
                      %s
                    </td></tr>
                  </table>
                </td></tr>
                """.formatted(escapeHtml(item.bookTitle()), escapeHtml(item.loanNumber()), escapeHtml(item.copyBarcode()),
                    item.dueDate().atZoneSameInstant(VN_ZONE).format(DATE_FORMATTER), overdue ? "#b91c1c" : "#2563eb",
                    status, item.daysOverdue(), overdue ? formatCurrency(item.estimatedFeeVnd()) + " đ" : "0 đ (Trong hạn)",
                    overdue ? "<p style=\"margin:6px 0;color:#64748b;font-size:13px;\">Ngày mở cửa tính phí: "
                            + item.openOverdueDays() + "</p>" : ""));
        }
        String notice = overdue
                ? "Số tiền trên là <strong>phí tạm tính tại thời điểm gửi email</strong>, theo số ngày thư viện mở cửa thực tế, "
                    + "không tính các ngày đóng cửa và ngày nghỉ lễ đã khai báo. Đây chỉ là thông tin ước tính, "
                    + "chưa phải khoản thu chính thức; email này <strong>không tạo khoản phải thu mới</strong>. "
                    + "Thủ thư sẽ chốt phí khi làm thủ tục trả sách."
                : "Toàn bộ sách trên đều đang trong thời hạn mượn hợp lệ và chưa phát sinh phí trễ hạn. Vui lòng trả sách đúng hạn.";
        return """
            <!DOCTYPE html>
            <html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Thư viện LIBRA — Nhắc hạn trả sách</title></head>
            <body style="margin:0;padding:0;width:100%%;background-color:#f1f5f9;font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.6;color:#1e293b;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="width:100%%;table-layout:fixed;background-color:#f1f5f9;">
                <tr><td align="center" style="padding:12px 0;">
                  <!--[if mso]><table role="presentation" width="640" align="center"><tr><td><![endif]-->
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" align="center"
                    style="width:100%%;max-width:640px;table-layout:fixed;background-color:#ffffff;">
                    <tr><td style="padding:24px 20px;background-color:#1e3a8a;color:#ffffff;">
                      <p style="margin:0;font-size:24px;font-weight:bold;letter-spacing:2px;">LIBRA</p>
                      <p style="margin:4px 0 16px;color:#dbeafe;">THƯ VIỆN · MƯỢN VÀ TRẢ SÁCH</p>
                      <h1 style="margin:0;font-size:21px;line-height:1.4;">%s</h1>
                    </td></tr>
                    <tr><td style="padding:20px 16px;overflow-wrap:anywhere;word-wrap:break-word;word-break:break-word;">
                      <p style="margin:0 0 12px;">Kính gửi bạn đọc <strong>%s</strong>,</p>
                      <p style="margin:0;">Ngày nhắc: <strong>%s</strong> · <strong>%d cuốn sách</strong> cần chú ý hoàn trả.</p>
                      <p style="margin:12px 0 0;">Vui lòng mang sách đến quầy thư viện để làm thủ tục trả hoặc liên hệ thủ thư nếu cần hỗ trợ.</p>
                    </td></tr>
                    %s
                    <tr><td style="padding:16px;background-color:#eff6ff;">
                      <p style="margin:0;color:#475569;">Tổng phí trễ hạn tạm tính</p>
                      <p style="margin:4px 0;font-size:24px;font-weight:bold;color:#1e3a8a;">%s đ</p>
                    </td></tr>
                    <tr><td style="padding:20px 16px;font-size:13px;color:#475569;">%s</td></tr>
                    <tr><td style="padding:20px 16px;border-top:1px solid #e2e8f0;background-color:#f8fafc;font-size:12px;color:#64748b;">
                      Email tự động từ Hệ thống Quản lý Thư viện LIBRA.<br>
                      Nếu đã hoàn trả sách sau thời điểm lập thông báo, vui lòng bỏ qua email này. Liên hệ trực tiếp quầy thủ thư để được hỗ trợ.
                    </td></tr>
                  </table>
                  <!--[if mso]></td></tr></table><![endif]-->
                </td></tr>
              </table>
            </body></html>
            """.formatted(title, escapeHtml(readerName), targetDate.format(DATE_FORMATTER), items.size(), cards,
                formatCurrency(totalEstimatedFeeVnd), notice);
    }

    private void recordInitialLog(
            LocalDate targetDate,
            LoanReminderGroupDto group,
            String status,
            String errorMessage,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName,
            boolean isDryRun
    ) {
        String loanNumbers = group.items().stream().map(LoanReminderItemDto::loanNumber).distinct().collect(Collectors.joining(", "));
        String bookTitles = group.items().stream().map(LoanReminderItemDto::bookTitle).distinct().collect(Collectors.joining("; "));
        String itemsDetailJson;
        try {
            itemsDetailJson = objectMapper.writeValueAsString(group.items());
        } catch (Exception e) {
            itemsDetailJson = "[]";
        }

        String safeError = errorMessage;
        if (safeError != null && safeError.length() > 900) {
            safeError = safeError.substring(0, 900) + "...";
        }

        Long logId = repository.insertLog(
                targetDate,
                group.readerId(),
                group.readerName(),
                group.readerCardNumber(),
                group.recipientEmail(),
                group.reminderGroup(),
                group.items().size(),
                group.totalEstimatedFeeVnd(),
                0, // Initial send is 0 retries
                3, // Max 3 retries
                loanNumbers,
                bookTitles,
                itemsDetailJson,
                group.subject(),
                group.emailContentHtml(),
                status,
                safeError,
                triggeredBy != null ? triggeredBy : "SYSTEM_SCHEDULED",
                triggeredByUserId,
                triggeredByUserName,
                isDryRun
        );

        // Record attempt #1 in audit log attempts table
        if (logId != null) {
            if (!"SKIPPED".equals(status)) {
                repository.insertRetryAttempt(
                        logId,
                        1,
                        "SENT".equals(status) ? "SUCCESS" : "FAILED",
                        safeError,
                        triggeredBy != null ? triggeredBy : "SYSTEM_SCHEDULED"
                );
            }
        }
    }

    private String formatCurrency(long amount) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(amount);
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
