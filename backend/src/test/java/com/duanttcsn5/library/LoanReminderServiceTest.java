package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderExecutionResult;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderGroupDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderItemDto;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderPreviewResponse;
import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.LoanReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

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
    private LoanReminderService service;

    private final LocalDate targetDate = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        repository = mock(LoanReminderRepository.class);
        emailService = mock(EmailService.class);
        objectMapper = new ObjectMapper();
        service = new LoanReminderService(repository, emailService, objectMapper);
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
    void testConsolidateMultipleLoansForSameReaderSameGroup() {
        OffsetDateTime dueIn2 = targetDate.plusDays(2).atTime(15, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item1 = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                201L, "Mắt biếc", "BAR-001", dueIn2
        );
        EligibleLoanItemRow item2 = new EligibleLoanItemRow(
                2L, 101L, "PM-001", 10L, "Nguyễn Văn A", "vana@example.com",
                202L, "Tôi thấy hoa vàng trên cỏ xanh", "BAR-002", dueIn2
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item1, item2));

        LoanReminderPreviewResponse preview = service.getPreview(targetDate);
        assertEquals(1, preview.totalRecipients(), "Should consolidate into 1 email reminder");
        assertEquals(2, preview.totalItems());

        LoanReminderGroupDto group = preview.reminders().get(0);
        assertEquals("Nguyễn Văn A", group.readerName());
        assertEquals(2, group.items().size());
        assertTrue(group.emailContentHtml().contains("Mắt biếc"));
        assertTrue(group.emailContentHtml().contains("Tôi thấy hoa vàng trên cỏ xanh"));
        assertTrue(group.emailContentHtml().contains("0 ngày"));
        assertTrue(group.emailContentHtml().contains("0 đ"));
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

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "MANUAL_TEST_RUN", 1L, "Thủ thư A");

        assertEquals(1, result.totalRecipients());
        assertEquals(1, result.sentCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.skippedCount());

        verify(emailService).sendRawHtmlEmail(eq("bandoc@example.com"), anyString(), anyString());
        verify(repository).insertLog(
                eq(targetDate), eq(10L), eq("Bạn Đọc A"), eq("bandoc@example.com"),
                eq("DUE_TODAY"), eq(1), eq("PM-001"), eq("Sách Giáo Trình"),
                anyString(), anyString(), anyString(), eq("SENT"), any(),
                eq("MANUAL_TEST_RUN"), eq(1L), eq("Thủ thư A"), eq(false)
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
    void testEmailFailureMarksStatusFailed() throws Exception {
        OffsetDateTime dueToday = targetDate.atTime(10, 0).atOffset(VN_OFFSET);

        EligibleLoanItemRow item = new EligibleLoanItemRow(
                1L, 101L, "PM-001", 10L, "Bạn Đọc", "reader@example.com",
                201L, "Sách Test", "BAR-001", dueToday
        );

        when(repository.findEligibleItemsForDate(targetDate)).thenReturn(List.of(item));
        when(repository.isAlreadySentToday(any(), anyLong(), anyString())).thenReturn(false);
        doThrow(new RuntimeException("SMTP connection refused")).when(emailService)
                .sendRawHtmlEmail(anyString(), anyString(), anyString());

        LoanReminderExecutionResult result = service.processDailyReminders(targetDate, "SYSTEM_SCHEDULED", null, "Hệ thống");

        assertEquals(1, result.totalRecipients());
        assertEquals(0, result.sentCount());
        assertEquals(1, result.failedCount());

        verify(repository).insertLog(
                eq(targetDate), eq(10L), anyString(), anyString(),
                anyString(), anyInt(), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq("FAILED"),
                eq("SMTP connection refused"), anyString(), any(), any(), anyBoolean()
        );
    }
}
