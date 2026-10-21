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
        when(repository.claimInitial(any(), any(), anyString(), any(), any(), any())).thenReturn(1L);
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

        LoanReminderGroupDto group1 = preview.reminders().stream().filter(g -> g.readerId().equals(10L)).findFirst().orElseThrow();
        assertEquals(1L, group1.items().get(0).daysOverdue());
        assertTrue(group1.subject().contains("quá hạn hằng ngày"));

        LoanReminderGroupDto group2 = preview.reminders().stream().filter(g -> g.readerId().equals(11L)).findFirst().orElseThrow();
        assertEquals(7L, group2.items().get(0).daysOverdue());
        assertTrue(group2.subject().contains("quá hạn hằng ngày"));

        LoanReminderGroupDto group3 = preview.reminders().stream().filter(g -> g.readerId().equals(12L)).findFirst().orElseThrow();
        assertEquals(30L, group3.items().get(0).daysOverdue());
        assertTrue(group3.subject().contains("quá hạn hằng ngày"));
    }

    @Test
    void testAllOverdueDaysIncluded() {
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
        assertEquals(5, preview.totalRecipients());
        assertEquals(5, preview.totalItems());
    }

    @Test
    void testConsolidateMultipleLoansForSameReaderSameGroupWithFeeCalculationDemo() throws Exception {
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
        assertEquals("OVERDUE_DAILY", group.reminderGroup());
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
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/reminder-previews"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/reminder-previews/fee-demo.html"), html);
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

}
