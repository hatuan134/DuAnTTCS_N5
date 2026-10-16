package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderGroupDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderItemDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import com.duanttcsn5.library.service.LoanReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanReminderServiceTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);

    private LoanReminderRepository repository;
    private EmailService emailService;
    private ObjectMapper objectMapper;
    private LibraryConfigurationService libraryConfigService;
    private LoanReminderService service;

    private final LocalDate targetDate = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        repository = mock(LoanReminderRepository.class);
        emailService = mock(EmailService.class);
        objectMapper = new ObjectMapper();
        libraryConfigService = mock(LibraryConfigurationService.class);
        service = new LoanReminderService(repository, emailService, objectMapper, libraryConfigService);
    }

    @Test
    void testDueIn2DaysAndDueTodayClassification() {
        OffsetDateTime dueIn2 = targetDate.plusDays(2).atTime(17, 0).atOffset(VN_OFFSET);
        OffsetDateTime dueToday = targetDate.atTime(17, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item1 = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Lập trình Java", "BAR-001", dueIn2
        );
        EligibleLoanItemRow item2 = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 11L, "Trần Thị B", "thib@example.com",
                202L, "Cấu trúc dữ liệu", "BAR-002", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item1, item2));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(2, preview.totalRecipients());
        assertEquals(2, preview.totalItems());

        LoanReminderGroupDto group1 = preview.reminders().stream()
                .filter(r -> r.readerId().equals(10L)).findFirst().orElseThrow();
        assertEquals("DUE_IN_2_DAYS", group1.reminderGroup());
        assertTrue(group1.subject().contains("còn 2 ngày"));
        assertEquals(1, group1.items().size());
        assertEquals(0L, group1.items().get(0).daysOverdue());
        assertEquals(0L, group1.items().get(0).estimatedFeeVnd());

        LoanReminderGroupDto group2 = preview.reminders().stream()
                .filter(r -> r.readerId().equals(11L)).findFirst().orElseThrow();
        assertEquals("DUE_TODAY", group2.reminderGroup());
        assertTrue(group2.subject().contains("hôm nay"));
        assertEquals(1, group2.items().size());
        assertEquals(0L, group2.items().get(0).daysOverdue());
        assertEquals(0L, group2.items().get(0).estimatedFeeVnd());
    }

    @Test
    void testOverdue1Day7Days30DaysClassification() {
        OffsetDateTime due1DayAgo = targetDate.minusDays(1).atTime(17, 0).atOffset(VN_OFFSET);
        OffsetDateTime due7DaysAgo = targetDate.minusDays(7).atTime(17, 0).atOffset(VN_OFFSET);
        OffsetDateTime due30DaysAgo = targetDate.minusDays(30).atTime(17, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item1 = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Sách Quá Hạn 1 Ngày", "BAR-001", due1DayAgo
        );
        EligibleLoanItemRow item2 = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 11L, "Trần Thị B", "thib@example.com",
                202L, "Sách Quá Hạn 7 Ngày", "BAR-002", due7DaysAgo
        );
        EligibleLoanItemRow item3 = new EligibleLoanItemRow(
                3L, 103L, "PM-003", 12L, "Lê Văn C", "vanc@example.com",
                203L, "Sách Quá Hạn 30 Ngày", "BAR-003", due30DaysAgo
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item1, item2, item3));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(3, preview.totalRecipients());
        assertEquals(3, preview.totalItems());

        LoanReminderGroupDto group1 = preview.reminders().stream().filter(g -> g.reminderGroup().equals("OVERDUE_1_DAY")).findFirst().orElseThrow();
        assertEquals(1L, group1.items().get(0).daysOverdue());
        assertTrue(group1.subject().contains("quá hạn 1 ngày"));

        LoanReminderGroupDto group2 = preview.reminders().stream().filter(g -> g.reminderGroup().equals("OVERDUE_7_DAYS")).findFirst().orElseThrow();
        assertEquals(7L, group2.items().get(0).daysOverdue());
        assertTrue(group2.subject().contains("quá hạn 7 ngày"));

        LoanReminderGroupDto group3 = preview.reminders().stream().filter(g -> g.reminderGroup().equals("OVERDUE_30_DAYS")).findFirst().orElseThrow();
        assertEquals(30L, group3.items().get(0).daysOverdue());
        assertTrue(group3.subject().contains("quá hạn 30 ngày"));
    }

    @Test
    void testNonDesignatedOverdueDaysIgnored() {
        // Overdue by 2 days, 6 days, 8 days, 29 days, 31 days
        EligibleLoanItemRow item2Days = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Sách Quá Hạn 2 Ngày", "BAR-001", targetDate.minusDays(2).atTime(17, 0).atOffset(VN_OFFSET)
        );
        EligibleLoanItemRow item6Days = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 11L, "Trần Thị B", "thib@example.com",
                202L, "Sách Quá Hạn 6 Ngày", "BAR-002", targetDate.minusDays(6).atTime(17, 0).atOffset(VN_OFFSET)
        );
        EligibleLoanItemRow item8Days = new EligibleLoanItemRow(
                3L, 103L, "PM-003", 12L, "Lê Văn C", "vanc@example.com",
                203L, "Sách Quá Hạn 8 Ngày", "BAR-003", targetDate.minusDays(8).atTime(17, 0).atOffset(VN_OFFSET)
        );
        EligibleLoanItemRow item29Days = new EligibleLoanItemRow(
                4L, 104L, "PM-004", 13L, "Phạm Văn D", "vand@example.com",
                204L, "Sách Quá Hạn 29 Ngày", "BAR-004", targetDate.minusDays(29).atTime(17, 0).atOffset(VN_OFFSET)
        );
        EligibleLoanItemRow item31Days = new EligibleLoanItemRow(
                5L, 105L, "PM-005", 14L, "Hoàng Thị E", "thie@example.com",
                205L, "Sách Quá Hạn 31 Ngày", "BAR-005", targetDate.minusDays(31).atTime(17, 0).atOffset(VN_OFFSET)
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item2Days, item6Days, item8Days, item29Days, item31Days));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(0, preview.totalRecipients(), "Non-designated days must not produce reminders");
        assertEquals(0, preview.totalItems());
    }

    @Test
    void testConsolidateMultipleLoansForSameReaderSameGroupWithFeeCalculationDemo() {
        // Demo scenario: Reader A has 2 books overdue by exactly 7 days.
        // Book 1 estimated fee = 20,000 VND (e.g. 4 open days)
        // Book 2 estimated fee = 15,000 VND (e.g. 3 open days due to closure)
        OffsetDateTime due7DaysAgo = targetDate.minusDays(7).atTime(17, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item1 = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn đọc A", "bandoca@example.com",
                201L, "Cuốn sách số 1", "BAR-001", due7DaysAgo
        );
        EligibleLoanItemRow item2 = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 10L, "Bạn đọc A", "bandoca@example.com",
                202L, "Cuốn sách số 2", "BAR-002", due7DaysAgo
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item1, item2));

        ReturnFeeAssessment assessment1 = new ReturnFeeAssessment(
                "READY", "Phí trễ hạn", "V1", due7DaysAgo, targetDate.atTime(7, 0).atOffset(VN_OFFSET),
                4L, 5000L, 20000L, 20000L, null, List.of(), List.of(), List.of()
        );
        ReturnFeeAssessment assessment2 = new ReturnFeeAssessment(
                "READY", "Phí trễ hạn", "V1", due7DaysAgo, targetDate.atTime(7, 0).atOffset(VN_OFFSET),
                3L, 5000L, 15000L, 15000L, null, List.of(), List.of(), List.of()
        );

        when(libraryConfigService.previewReturnFee(eq(due7DaysAgo), any())).thenReturn(assessment1, assessment2);

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(1, preview.totalRecipients(), "Consolidated into 1 email for Reader A");
        assertEquals(2, preview.totalItems());
        assertEquals(35000L, preview.grandTotalEstimatedFeeVnd());

        LoanReminderGroupDto group = preview.reminders().get(0);
        assertEquals("OVERDUE_7_DAYS", group.reminderGroup());
        assertEquals(35000L, group.totalEstimatedFeeVnd());
        assertEquals(2, group.items().size());
        assertEquals(20000L, group.items().get(0).estimatedFeeVnd());
        assertEquals(15000L, group.items().get(1).estimatedFeeVnd());

        // Check HTML content
        String html = group.emailContentHtml();
        assertTrue(html.contains("Cuốn sách số 1"));
        assertTrue(html.contains("Cuốn sách số 2"));
        assertTrue(html.contains("20.000 đ"));
        assertTrue(html.contains("15.000 đ"));
        assertTrue(html.contains("35.000 đ"));
        assertTrue(html.contains("Quá hạn 7 ngày"));
        assertTrue(html.contains("phí tạm tính tại thời điểm gửi email"));
        assertTrue(html.contains("không tạo khoản phải thu mới"));
    }

    @Test
    void testEmailContentDueTodayContainsOptimizedStatusAndFootnote() {
        OffsetDateTime dueToday = targetDate.atTime(15, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Chí Phèo", "BAR-001", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        LoanReminderGroupDto group = preview.reminders().get(0);

        assertEquals("DUE_TODAY", group.reminderGroup());
        assertTrue(group.emailContentHtml().contains("Đến hạn hôm nay"));
        assertTrue(group.emailContentHtml().contains("0 đ (Trong hạn)"));
        assertTrue(group.emailContentHtml().contains("Toàn bộ sách trên đều đang trong thời hạn mượn hợp lệ"));
    }

    @Test
    void testBlankOrNullEmailIsSkippedSafely() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow itemNullEmail = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc Email Null", null,
                201L, "Sách 1", "BAR-001", dueToday
        );
        EligibleLoanItemRow itemEmptyEmail = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 11L, "Bạn Đọc Email Rỗng", "   ",
                202L, "Sách 2", "BAR-002", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(itemNullEmail, itemEmptyEmail));
        when(repository.isAlreadySentToday(any(), anyLong(), anyString())).thenReturn(false);

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "SYSTEM_SCHEDULED", null, "Hệ thống");

        assertEquals(2, result.totalRecipients());
        assertEquals(0, result.sentCount());
        assertEquals(2, result.skippedCount());
        verify(emailService, never()).sendRawHtmlEmail(any(), any(), any());
    }

    @Test
    void testSameReaderWithTwoDifferentGroupsGeneratesTwoReminders() {
        OffsetDateTime dueIn2 = targetDate.plusDays(2).atTime(15, 0).atOffset(VN_OFFSET);
        OffsetDateTime dueToday = targetDate.atTime(15, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item1 = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Sách 1", "BAR-001", dueIn2
        );
        EligibleLoanItemRow item2 = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 10L, "Nguyễn Văn A", "vana@example.com",
                202L, "Sách 2", "BAR-002", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item1, item2));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(2, preview.totalRecipients(), "Reader with books in both groups receives 2 separate emails");
        assertEquals(2, preview.totalItems());

        List<String> groupNames = preview.reminders().stream().map(LoanReminderGroupDto::reminderGroup).toList();
        assertTrue(groupNames.contains("DUE_IN_2_DAYS"));
        assertTrue(groupNames.contains("DUE_TODAY"));
    }

    @Test
    void testProcessRemindersSendsEmailsAndPreventsDuplicateRun() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc A", "bandoc@example.com",
                201L, "Sách Giáo Trình", "BAR-001", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item));
        when(repository.isAlreadySentToday(targetDate, 10L, "DUE_TODAY")).thenReturn(false);
        when(repository.insertLog(
                any(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyInt(), anyLong(), anyInt(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), any(), anyBoolean()
        )).thenReturn(1001L);

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "MANUAL_TEST_RUN", 1L, "Thủ thư A");

        assertEquals(1, result.totalRecipients());
        assertEquals(1, result.sentCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.skippedCount());

        verify(emailService).sendRawHtmlEmail(eq("bandoc@example.com"), anyString(), anyString());
        verify(repository).insertLog(
                eq(targetDate), eq(10L), eq("Bạn Đọc A"), any(), eq("bandoc@example.com"),
                eq("DUE_TODAY"), eq(1), eq(0L), eq(0), eq(3), eq("PM-001"), eq("Sách Giáo Trình"),
                anyString(), anyString(), anyString(), eq("SENT"), any(),
                eq("MANUAL_TEST_RUN"), eq(1L), eq("Thủ thư A"), eq(false)
        );
        verify(repository).insertRetryAttempt(
                eq(1001L), eq(1), eq("SUCCESS"), any(), eq("MANUAL_TEST_RUN")
        );

        // Subsequent run on the same day: already sent -> must be skipped!
        when(repository.isAlreadySentToday(targetDate, 10L, "DUE_TODAY")).thenReturn(true);
        LoanReminderExecutionResult secondRun = service.processDailyReminders(targetDate, "MANUAL_TEST_RUN", 1L, "Thủ thư A");

        assertEquals(1, secondRun.totalRecipients());
        assertEquals(0, secondRun.sentCount(), "Second run must not send duplicate email");
        assertEquals(1, secondRun.skippedCount());
    }

    @Test
    void testInvalidOrBlankEmailIsSkippedWithoutBreakingWorkflow() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow itemInvalid = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc Không Email", "invalid-email-format",
                201L, "Sách 1", "BAR-001", dueToday
        );
        EligibleLoanItemRow itemValid = new EligibleLoanItemRow(
                2L, 102L, "PM-002", 11L, "Bạn Đọc Chuẩn", "valid@example.com",
                202L, "Sách 2", "BAR-002", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(itemInvalid, itemValid));
        when(repository.isAlreadySentToday(any(), anyLong(), anyString())).thenReturn(false);

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "SYSTEM_SCHEDULED", null, "Hệ thống");

        assertEquals(2, result.totalRecipients());
        assertEquals(1, result.sentCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.failedCount());

        verify(emailService, never()).sendRawHtmlEmail(eq("invalid-email-format"), anyString(), anyString());
        verify(emailService).sendRawHtmlEmail(eq("valid@example.com"), anyString(), anyString());
    }

    @Test
    void testEmailInitialFailureMovesToPendingRetryAndRecordsAttempt1() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc", "reader@example.com",
                201L, "Sách Test", "BAR-001", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item));
        when(repository.isAlreadySentToday(any(), anyLong(), anyString())).thenReturn(false);
        when(repository.insertLog(
                any(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyInt(), anyLong(), anyInt(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), any(), anyBoolean()
        )).thenReturn(2001L);

        doThrow(new RuntimeException("SMTP connection refused")).when(emailService)
                .sendRawHtmlEmail(anyString(), anyString(), anyString());

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "SYSTEM_SCHEDULED", null, "Hệ thống");

        assertEquals(1, result.totalRecipients());
        assertEquals(0, result.sentCount());
        assertEquals(1, result.failedCount());

        // Status is PENDING_RETRY on initial failure
        verify(repository).insertLog(
                eq(targetDate), eq(10L), anyString(), any(), eq("reader@example.com"),
                anyString(), anyInt(), eq(0L), eq(0), eq(3), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq("PENDING_RETRY"),
                eq(com.duanttcsn5.library.service.LoanReminderFailureMessages.message("SMTP connection refused")), eq("SYSTEM_SCHEDULED"), any(), any(), anyBoolean()
        );
        verify(repository).insertRetryAttempt(
                eq(2001L), eq(1), eq("FAILED"), eq(com.duanttcsn5.library.service.LoanReminderFailureMessages.message("SMTP connection refused")),
                eq("SYSTEM_SCHEDULED")
        );
    }

    @Test
    void testRetryPendingSuccessOnFirstRetryUpdatesToSent() throws Exception {
        var pendingLog = new com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse(
                100L, targetDate, 10L, "Bạn Đọc A", "CARD-001", "bandoc@example.com",
                "OVERDUE_7_DAYS", 1, 25000L, 0, 3, "PM-001", "Sách 1", "[]",
                "[Thư viện] Nhắc quá hạn", "<p>Phí 25.000 đ</p>", "PENDING_RETRY",
                "Connection timeout", "SYSTEM_SCHEDULED", "Hệ thống",
                false, OffsetDateTime.now(VN_OFFSET), List.of()
        );

        when(repository.findLogById(100L)).thenReturn(Optional.of(pendingLog));

        var retryResult = service.processPendingRetries(100L, false, 1L, "Thủ thư A");

        assertEquals(1, retryResult.totalProcessed());
        assertEquals(1, retryResult.succeededCount());
        assertEquals(0, retryResult.stillPendingCount());
        assertEquals(0, retryResult.maxRetriesReachedCount());

        verify(emailService).sendRawHtmlEmail(eq("bandoc@example.com"), eq("[Thư viện] Nhắc quá hạn"), eq("<p>Phí 25.000 đ</p>"));
        // Attempt #2 (first retry, retryCount = 1)
        verify(repository).insertRetryAttempt(eq(100L), eq(2), eq("SUCCESS"), any(), eq("MANUAL_RETRY (Thủ thư A)"));
        verify(repository).updateLogRetryStatus(eq(100L), eq("SENT"), eq(1), any());
    }

    @Test
    void testRetryPendingExhaustsAfter3RetriesMarksFailedMaxRetries() throws Exception {
        // Already retried twice (retryCount = 2, so this is attempt #4)
        var pendingLog = new com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse(
                101L, targetDate, 10L, "Bạn Đọc B", "CARD-002", "bandocb@example.com",
                "OVERDUE_7_DAYS", 1, 25000L, 2, 3, "PM-002", "Sách 2", "[]",
                "[Thư viện] Nhắc quá hạn", "<p>Nội dung gốc</p>", "PENDING_RETRY",
                "Network down", "SYSTEM_SCHEDULED", "Hệ thống",
                false, OffsetDateTime.now(VN_OFFSET), List.of()
        );

        when(repository.findLogById(101L)).thenReturn(Optional.of(pendingLog));
        doThrow(new RuntimeException("Mailbox unavailable")).when(emailService)
                .sendRawHtmlEmail(anyString(), anyString(), anyString());

        var retryResult = service.processPendingRetries(101L, false, 1L, "Thủ thư A");

        assertEquals(1, retryResult.totalProcessed());
        assertEquals(0, retryResult.succeededCount());
        assertEquals(0, retryResult.stillPendingCount());
        assertEquals(1, retryResult.maxRetriesReachedCount(), "Should mark as exhausted after 3rd retry failure");

        // Attempt #4 recorded
        verify(repository).insertRetryAttempt(eq(101L), eq(4), eq("FAILED"), eq(com.duanttcsn5.library.service.LoanReminderFailureMessages.message("Mailbox unavailable")), eq("MANUAL_RETRY (Thủ thư A)"));
        // Status updated to FAILED_MAX_RETRIES, retry_count = 3
        verify(repository).updateLogRetryStatus(eq(101L), eq("FAILED_MAX_RETRIES"), eq(3), eq(com.duanttcsn5.library.service.LoanReminderFailureMessages.message("Mailbox unavailable")));
    }

    @Test
    void testExhaustedLogDoesNotAllowFourthRetry() {
        // Log is already in FAILED_MAX_RETRIES
        var exhaustedLog = new com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse(
                102L, targetDate, 10L, "Bạn Đọc C", "CARD-003", "bandocc@example.com",
                "OVERDUE_30_DAYS", 1, 90000L, 3, 3, "PM-003", "Sách 3", "[]",
                "[Thư viện] Nhắc", "<p>Content</p>", "FAILED_MAX_RETRIES",
                "3 retries failed", "SYSTEM_SCHEDULED", "Hệ thống",
                false, OffsetDateTime.now(VN_OFFSET), List.of()
        );

        when(repository.findLogById(102L)).thenReturn(Optional.of(exhaustedLog));

        var retryResult = service.processPendingRetries(102L, false, 1L, "Thủ thư A");

        assertEquals(0, retryResult.totalProcessed());
    }

    @Test
    void testSimulateFailureFlagForcesFailureAndPendingRetry() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc Sim", "sim@example.com",
                201L, "Sách Sim", "BAR-SIM", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item));
        when(repository.isAlreadySentToday(any(), anyLong(), anyString())).thenReturn(false);
        when(repository.insertLog(
                any(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyInt(), anyLong(), anyInt(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), any(), anyBoolean()
        )).thenReturn(3001L);

        // Run with simulateFailure = true
        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "MANUAL_TEST_RUN", 1L, "Thủ thư A", true);

        assertEquals(1, result.totalRecipients());
        assertEquals(0, result.sentCount());
        assertEquals(1, result.failedCount());

        verify(emailService, never()).sendRawHtmlEmail(anyString(), anyString(), anyString());
        verify(repository).insertLog(
                eq(targetDate), eq(10L), anyString(), any(), eq("sim@example.com"),
                anyString(), anyInt(), eq(0L), eq(0), eq(3), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq("PENDING_RETRY"),
                eq("Mô phỏng lỗi gửi thư tạm thời (để kiểm thử cơ chế tự động thử lại)"),
                eq("MANUAL_TEST_RUN"), eq(1L), eq("Thủ thư A"), eq(false)
        );
    }
}
