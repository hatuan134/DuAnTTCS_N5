package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanReminderEmailTest {
    @Test void dailyGroupPreservesIndividualDatesEscapesDataAndExportsResponsivePreview() throws Exception {
        var repo=mock(LoanReminderRepository.class);
        var mail=mock(EmailService.class);
        LocalDate date=LocalDate.of(2026,10,21);
        List<EligibleLoanItemRow> rows=new ArrayList<>();
        for(int days:List.of(1,2,5,7,10,30,31,100)) rows.add(new EligibleLoanItemRow(
                (long)days,1L,"PM-"+days,1L,"Nguyễn Thị Ánh <script>alert(1)</script>","reader@example.test",1L,
                "Nghiên cứu văn học Việt Nam & thế giới — " + "Tên sách rất dài ".repeat(12) + "<img src=x onerror=alert(1)>",
                "BARCODE"+"X".repeat(120),date.minusDays(days).atTime(17,0).atOffset(ZoneOffset.ofHours(7))));
        when(repo.findEligibleItemsForDate(date)).thenReturn(rows);
        var service=new LoanReminderService(repo,mail,new ObjectMapper());
        var preview=service.getPreview(date);
        assertEquals(1,preview.totalRecipients());
        var group=preview.reminders().get(0);
        assertEquals("OVERDUE_DAILY",group.reminderGroup());
        assertEquals(List.of(1L,2L,5L,7L,10L,30L,31L,100L),group.items().stream().map(i->i.daysOverdue()).toList());
        String html=group.emailContentHtml();
        assertFalse(html.contains("<script>"));assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("&amp;"));
        assertTrue(html.contains("table-layout:fixed")); assertTrue(html.contains("max-width:640px"));
        assertFalse(html.contains("<th>")); assertFalse(html.contains("min-width:"));
        assertTrue(html.contains("Ngày hạn trả")); assertTrue(html.contains("Mã phiếu mượn"));
        assertTrue(html.contains("Mã vạch bản sao"));assertTrue(html.contains("Số ngày quá hạn"));
        assertTrue(html.contains("Tổng phí trễ hạn tạm tính"));
        Files.createDirectories(Path.of("target/reminder-previews"));
        Files.writeString(Path.of("target/reminder-previews/many-books.html"),html);
        when(repo.findEligibleItemsForDate(date)).thenReturn(List.of(rows.get(0)));
        String one=service.getPreview(date).reminders().get(0).emailContentHtml();
        Files.writeString(Path.of("target/reminder-previews/one-book.html"),one);
        verifyNoInteractions(mail);
    }
    @Test void previewSeparatesReadySentProcessingPendingAndReview() {
        var repo=mock(LoanReminderRepository.class);
        LocalDate date=LocalDate.of(2026,10,21);
        when(repo.findEligibleItemsForDate(date)).thenReturn(List.of(new EligibleLoanItemRow(
                1L,1L,"PM-1",1L,"Reader","reader@example.test",1L,"Sách","BC",date.atTime(17,0).atOffset(ZoneOffset.ofHours(7)))));
        var service=new LoanReminderService(repo,mock(EmailService.class),new ObjectMapper());
        for(String state:List.of("READY","SENT","PROCESSING","PENDING_RETRY","REVIEW_REQUIRED","FAILED_MAX_RETRIES")) {
            when(repo.deliveryState(date,1L,"DUE_TODAY")).thenReturn(state);
            var group=service.getPreview(date).reminders().get(0);
            assertEquals(state,group.deliveryState());
            assertEquals(!state.equals("READY"),group.alreadySentToday());
        }
    }
}
