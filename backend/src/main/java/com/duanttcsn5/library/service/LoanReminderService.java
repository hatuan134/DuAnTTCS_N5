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

            // 1. Prevent duplicate sending on the same day for the same group (idempotent)
            if (repository.isAlreadySentToday(targetDate, group.readerId(), group.reminderGroup())) {
                log.info("Reminder already sent or pending retry today for readerId={} group={}; skipping", group.readerId(), group.reminderGroup());
                skippedCount++;
                continue;
            }

            // 2. Validate email: permanent invalid email is skipped immediately and NOT put into retry queue
            String email = group.recipientEmail() != null ? group.recipientEmail().trim() : "";
            if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
                log.warn("Invalid or missing email for readerId={}: '{}'", group.readerId(), email);
                try {
                    recordInitialLog(targetDate, group, "SKIPPED", "Địa chỉ email không hợp lệ hoặc bị trống",
                            triggeredBy, triggeredByUserId, triggeredByUserName, false);
                } catch (Exception ex) {
                    log.error("Failed to record skipped log for readerId={}: {}", group.readerId(), ex.getMessage());
                }
                skippedCount++;
                continue;
            }

            // 3. Attempt initial email dispatch
            if (shouldSimulateFailure) {
                log.info("Simulating initial email failure for readerId={} group={}", group.readerId(), group.reminderGroup());
                String errorReason = "Mô phỏng lỗi gửi thư tạm thời (để kiểm thử cơ chế tự động thử lại)";
                recordInitialLog(targetDate, group, "PENDING_RETRY", errorReason,
                        triggeredBy, triggeredByUserId, triggeredByUserName, true);
                failedCount++;
                continue;
            }

            try {
                emailService.sendRawHtmlEmail(email, group.subject(), group.emailContentHtml());
                recordInitialLog(targetDate, group, "SENT", null,
                        triggeredBy, triggeredByUserId, triggeredByUserName, false);
                sentCount++;
                log.info("Sent reminder email to {} for group {} with total estimated fee {} VND",
                        email, group.reminderGroup(), group.totalEstimatedFeeVnd());
            } catch (Exception ex) {
                log.error("Failed to send reminder email to {} for group {}: {}", email, group.reminderGroup(), ex.getMessage(), ex);
                String reason = LoanReminderFailureMessages.from(ex);
                // Temporary dispatch failure is placed in PENDING_RETRY queue for up to 3 automatic retries
                recordInitialLog(targetDate, group, "PENDING_RETRY", reason,
                        triggeredBy, triggeredByUserId, triggeredByUserName, true);
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

        int succeededCount = 0;
        int stillPendingCount = 0;
        int maxRetriesReachedCount = 0;
        boolean shouldSimulateFailure = Boolean.TRUE.equals(simulateFailure);

        for (LoanReminderLogResponse logItem : pendingLogs) {
            int currentRetry = logItem.retryCount();
            int newRetryCount = currentRetry + 1;
            int attemptNumber = currentRetry + 2; // Attempt 1 was initial, attempt 2 is retry 1, etc.
            String triggerSource = triggeredByUserName != null ? "MANUAL_RETRY (" + triggeredByUserName + ")" : "AUTO_RETRY";

            if (shouldSimulateFailure) {
                String errorMsg = "Mô phỏng lỗi thử lại lần " + newRetryCount;
                if (newRetryCount >= logItem.maxRetries()) {
                    repository.updateLogRetryStatus(logItem.id(), "FAILED_MAX_RETRIES", newRetryCount, errorMsg);
                    maxRetriesReachedCount++;
                } else {
                    repository.updateLogRetryStatus(logItem.id(), "PENDING_RETRY", newRetryCount, errorMsg);
                    stillPendingCount++;
                }
                repository.insertRetryAttempt(logItem.id(), attemptNumber, "FAILED", errorMsg, triggerSource);
                continue;
            }

            try {
                // Send with preserved original subject and email content!
                emailService.sendRawHtmlEmail(logItem.recipientEmail(), logItem.subject(), logItem.emailContent());
                repository.updateLogRetryStatus(logItem.id(), "SENT", newRetryCount, null);
                repository.insertRetryAttempt(logItem.id(), attemptNumber, "SUCCESS", null, triggerSource);
                succeededCount++;
                log.info("Retry attempt {} succeeded for logId={}", newRetryCount, logItem.id());
            } catch (Exception ex) {
                String reason = LoanReminderFailureMessages.from(ex);
                log.warn("Retry attempt {} failed for logId={}: {}", newRetryCount, logItem.id(), reason);

                if (newRetryCount >= logItem.maxRetries()) {
                    repository.updateLogRetryStatus(logItem.id(), "FAILED_MAX_RETRIES", newRetryCount, reason);
                    maxRetriesReachedCount++;
                } else {
                    repository.updateLogRetryStatus(logItem.id(), "PENDING_RETRY", newRetryCount, reason);
                    stillPendingCount++;
                }
                repository.insertRetryAttempt(logItem.id(), attemptNumber, "FAILED", reason, triggerSource);
            }
        }

        return new LoanReminderRetryExecutionResult(
                pendingLogs.size(),
                succeededCount,
                stillPendingCount,
                maxRetriesReachedCount
        );
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
        LocalDate overdue1 = targetDate.minusDays(1);
        LocalDate overdue7 = targetDate.minusDays(7);
        LocalDate overdue30 = targetDate.minusDays(30);

        // Group key: readerId + ":" + reminderGroup
        Map<String, List<EligibleLoanItemRow>> grouped = new LinkedHashMap<>();

        for (EligibleLoanItemRow item : items) {
            LocalDate dueLocalDate = item.dueDate().atZoneSameInstant(VN_ZONE).toLocalDate();
            String groupKey;
            if (dueLocalDate.equals(in2Days)) {
                groupKey = item.readerId() + ":DUE_IN_2_DAYS";
            } else if (dueLocalDate.equals(targetDate)) {
                groupKey = item.readerId() + ":DUE_TODAY";
            } else if (dueLocalDate.equals(overdue1)) {
                groupKey = item.readerId() + ":OVERDUE_1_DAY";
            } else if (dueLocalDate.equals(overdue7)) {
                groupKey = item.readerId() + ":OVERDUE_7_DAYS";
            } else if (dueLocalDate.equals(overdue30)) {
                groupKey = item.readerId() + ":OVERDUE_30_DAYS";
            } else {
                continue; // Do not send reminders for non-designated days (e.g. days 2..6, 8..29, 31+)
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
            boolean alreadySent = repository.isAlreadySentToday(targetDate, first.readerId(), groupType);
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
                    alreadySent
            ));
        }

        return result;
    }

    private String buildSubject(String groupType) {
        return switch (groupType) {
            case "DUE_IN_2_DAYS" -> "[Thư viện Libra] Nhắc nhở: Sách mượn sắp đến hạn trả (còn 2 ngày)";
            case "DUE_TODAY" -> "[Thư viện Libra] Thông báo: Sách mượn đến hạn trả hôm nay";
            case "OVERDUE_1_DAY" -> "[Thư viện Libra] Cảnh báo: Sách mượn đã quá hạn 1 ngày";
            case "OVERDUE_7_DAYS" -> "[Thư viện Libra] Cảnh báo: Sách mượn đã quá hạn 7 ngày - Đang phát sinh phí phạt";
            case "OVERDUE_30_DAYS" -> "[Thư viện Libra] Cảnh báo nghiêm trọng: Sách mượn đã quá hạn 30 ngày";
            default -> "[Thư viện Libra] Thông báo mượn trả sách";
        };
    }

    private String buildEmailHtml(
            String readerName,
            String groupType,
            LocalDate targetDate,
            List<LoanReminderItemDto> items,
            long totalEstimatedFeeVnd
    ) {
        boolean isDueIn2Days = "DUE_IN_2_DAYS".equals(groupType);
        boolean isDueToday = "DUE_TODAY".equals(groupType);
        boolean isOverdue = groupType.startsWith("OVERDUE_");

        String formattedDate = targetDate.format(DATE_FORMATTER);
        String formattedIn2Days = targetDate.plusDays(2).format(DATE_FORMATTER);

        String headerGradient;
        String headerTitle;
        String alertBg;
        String alertBorder;
        String alertMessage;

        switch (groupType) {
            case "DUE_IN_2_DAYS" -> {
                headerGradient = "linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%)";
                headerTitle = "Sách mượn sắp đến hạn trả (còn 2 ngày)";
                alertBg = "#eff6ff";
                alertBorder = "#3b82f6";
                alertMessage = String.format("Bạn có <strong>%d</strong> cuốn sách sắp đến hạn trả vào ngày <strong>%s</strong> (sau 2 ngày nữa). Vui lòng sắp xếp mang sách tới thư viện trả đúng hạn để tránh phát sinh phí trễ hạn.", items.size(), formattedIn2Days);
            }
            case "DUE_TODAY" -> {
                headerGradient = "linear-gradient(135deg, #92400e 0%, #d97706 100%)";
                headerTitle = "Sách mượn đến hạn trả hôm nay";
                alertBg = "#fef3c7";
                alertBorder = "#f59e0b";
                alertMessage = String.format("Bạn có <strong>%d</strong> cuốn sách đến hạn trả <strong>hôm nay</strong> (ngày <strong>%s</strong>). Vui lòng mang sách tới quầy thư viện trả trong ngày hôm nay.", items.size(), formattedDate);
            }
            case "OVERDUE_1_DAY" -> {
                headerGradient = "linear-gradient(135deg, #c2410c 0%, #ea580c 100%)";
                headerTitle = "Cảnh báo: Sách mượn đã quá hạn 1 ngày";
                alertBg = "#fff7ed";
                alertBorder = "#ea580c";
                alertMessage = String.format("Bạn có <strong>%d</strong> cuốn sách đã <strong>quá hạn trả 1 ngày</strong> tính đến ngày <strong>%s</strong>. Vui lòng mang sách tới thư viện hoàn trả ngay hôm nay để tránh tiếp tục phát sinh thêm phí trễ hạn.", items.size(), formattedDate);
            }
            case "OVERDUE_7_DAYS" -> {
                headerGradient = "linear-gradient(135deg, #991b1b 0%, #dc2626 100%)";
                headerTitle = "Cảnh báo: Sách mượn đã quá hạn 7 ngày";
                alertBg = "#fef2f2";
                alertBorder = "#ef4444";
                alertMessage = String.format("Bạn có <strong>%d</strong> cuốn sách đã <strong>quá hạn trả 7 ngày</strong> tính đến ngày <strong>%s</strong>. Phí trễ hạn đang tiếp tục được cộng dồn theo các ngày thư viện mở cửa. Vui lòng khẩn trương hoàn trả sách tại quầy thư viện.", items.size(), formattedDate);
            }
            case "OVERDUE_30_DAYS" -> {
                headerGradient = "linear-gradient(135deg, #4c0519 0%, #881337 100%)";
                headerTitle = "Cảnh báo nghiêm trọng: Sách mượn đã quá hạn 30 ngày";
                alertBg = "#fff1f2";
                alertBorder = "#e11d48";
                alertMessage = String.format("CẢNH BÁO: Bạn có <strong>%d</strong> cuốn sách đã <strong>quá hạn trả 30 ngày</strong> tính đến ngày <strong>%s</strong>. Việc trễ hạn kéo dài ảnh hưởng nghiêm trọng tới tài nguyên thư viện và tài khoản thẻ của bạn có thể bị tạm khóa. Vui lòng liên hệ thủ thư và hoàn trả sách ngay lập tức.", items.size(), formattedDate);
            }
            default -> {
                headerGradient = "linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%)";
                headerTitle = "Thông báo mượn trả sách";
                alertBg = "#f8fafc";
                alertBorder = "#64748b";
                alertMessage = String.format("Bạn có <strong>%d</strong> cuốn sách cần chú ý hoàn trả cho thư viện.", items.size());
            }
        }

        StringBuilder rows = new StringBuilder();
        int idx = 1;
        for (LoanReminderItemDto item : items) {
            String dueStr = item.dueDate() != null
                    ? item.dueDate().atZoneSameInstant(VN_ZONE).format(DATE_FORMATTER)
                    : "";

            String statusColHtml;
            String feeColHtml;

            if (isOverdue) {
                String openDaysHint = item.openOverdueDays() != item.daysOverdue()
                        ? String.format("<div style=\"font-size: 11px; color: #64748b;\">(%d ngày mở cửa tính phí)</div>", item.openOverdueDays())
                        : "";
                statusColHtml = String.format("""
                    <div style="font-weight: 600; color: #dc2626;">Quá hạn %d ngày</div>
                    %s
                    """, item.daysOverdue(), openDaysHint);
                feeColHtml = String.format("""
                    <div style="font-weight: 700; color: #dc2626; font-size: 14px;">%s đ</div>
                    <div style="font-size: 11px; color: #64748b;">(Tạm tính)</div>
                    """, formatCurrency(item.estimatedFeeVnd()));
            } else if (isDueIn2Days) {
                statusColHtml = "<span style=\"font-weight: 600; color: #2563eb;\">Còn 2 ngày</span>";
                feeColHtml = "<span style=\"font-weight: 500; color: #16a34a;\">0 đ (Trong hạn)</span>";
            } else {
                statusColHtml = "<span style=\"font-weight: 600; color: #d97706;\">Đến hạn hôm nay</span>";
                feeColHtml = "<span style=\"font-weight: 500; color: #16a34a;\">0 đ (Trong hạn)</span>";
            }

            rows.append(String.format("""
                <tr style="border-bottom: 1px solid #e2e8f0;">
                    <td style="padding: 10px; text-align: center;">%d</td>
                    <td style="padding: 10px; font-weight: 500;">%s</td>
                    <td style="padding: 10px;">%s</td>
                    <td style="padding: 10px; text-align: center; color: #475569;">%s</td>
                    <td style="padding: 10px; text-align: center; font-weight: 600; color: #2563eb;">%s</td>
                    <td style="padding: 10px; text-align: center;">%s</td>
                    <td style="padding: 10px; text-align: right;">%s</td>
                </tr>
                """, idx++, escapeHtml(item.loanNumber()), escapeHtml(item.bookTitle()),
                    escapeHtml(item.copyBarcode()), dueStr, statusColHtml, feeColHtml));
        }

        String totalRowHtml = "";
        if (isOverdue) {
            totalRowHtml = String.format("""
                <tr style="background-color: #f8fafc; font-weight: 700;">
                    <td colspan="6" style="padding: 12px 10px; text-align: right; color: #1e293b; font-size: 13px; text-transform: uppercase;">
                        Tổng phí trễ hạn tạm tính (%d cuốn):
                    </td>
                    <td style="padding: 12px 10px; text-align: right; color: #dc2626; font-size: 15px;">
                        %s đ
                    </td>
                </tr>
                """, items.size(), formatCurrency(totalEstimatedFeeVnd));
        }

        String noticeBoxHtml;
        if (isOverdue) {
            noticeBoxHtml = """
                <div style="margin-top: 24px; padding: 14px 18px; background-color: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; font-size: 13px; color: #92400e; line-height: 1.5;">
                    <p style="margin: 0 0 6px 0; font-weight: 700;">* Quy định quan trọng về phí trễ hạn tạm tính:</p>
                    <ul style="margin: 0; padding-left: 20px;">
                        <li>Số tiền nêu trên là <strong>phí tạm tính tại thời điểm gửi email</strong>, tính theo số ngày thư viện mở cửa thực tế (không tính các ngày đóng cửa và ngày nghỉ lễ đã được khai báo theo lịch thư viện).</li>
                        <li>Đây <strong>chỉ là thông tin ước tính, chưa phải là khoản thu chính thức</strong>. Hệ thống <strong>không tạo khoản phải thu mới</strong> khi gửi email nhắc nhở này.</li>
                        <li>Khoản phí chính thức sẽ được thủ thư chốt chính xác khi bạn đọc trực tiếp mang sách tới quầy làm thủ tục trả sách.</li>
                    </ul>
                </div>
                """;
        } else {
            noticeBoxHtml = """
                <p style="margin-top: 24px; font-size: 13px; color: #64748b;">
                    * Lưu ý: Toàn bộ sách trên đều đang trong thời hạn mượn hợp lệ và chưa phát sinh bất kỳ khoản phí phạt nào. Bạn đọc hãy mang sách tới trả đúng hạn để bảo đảm quyền lợi mượn sách.
                </p>
                """;
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1e293b; background-color: #f8fafc; margin: 0; padding: 20px; }
                    .card { max-width: 720px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }
                    .header { background: %s; color: #ffffff; padding: 24px; text-align: center; }
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
                        <p style="margin: 4px 0 0 0; opacity: 0.95; font-size: 14px;">%s</p>
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
                                    <th style="text-align: center; width: 35px;">STT</th>
                                    <th>Mã phiếu</th>
                                    <th>Tên sách</th>
                                    <th style="text-align: center;">Mã vạch</th>
                                    <th style="text-align: center;">Hạn trả</th>
                                    <th style="text-align: center;">Tình trạng</th>
                                    <th style="text-align: right;">Phí tạm tính</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                                %s
                            </tbody>
                        </table>
                        %s
                    </div>
                    <div class="footer">
                        <p style="margin: 0;">Email này được gửi tự động từ Hệ thống Quản lý Thư viện Libra.</p>
                        <p style="margin: 4px 0 0 0;">Nếu cần hỗ trợ thêm, bạn đọc vui lòng liên hệ trực tiếp quầy thủ thư thư viện.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                headerGradient,
                alertBg,
                alertBorder,
                headerTitle,
                escapeHtml(readerName),
                alertMessage,
                rows.toString(),
                totalRowHtml,
                noticeBoxHtml
        );
    }

    private void recordInitialLog(
            LocalDate targetDate,
            LoanReminderGroupDto group,
            String status,
            String errorMessage,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName,
            boolean isRetryableFailure
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
                false
        );

        // Record attempt #1 in audit log attempts table
        if (logId != null) {
            String attemptStatus = "SENT".equals(status) ? "SUCCESS" : ("SKIPPED".equals(status) ? "SKIPPED" : "FAILED");
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
