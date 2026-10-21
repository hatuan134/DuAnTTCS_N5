package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.LoanReminderRepository;
import com.duanttcsn5.library.repository.LoanReminderRepository.EligibleLoanItemRow;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailPreparationException;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class LoanReminderReliabilityTest {
    LoanReminderRepository repo;
    EmailService mail;
    LoanReminderService service;
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void setup() {
        repo = mock(LoanReminderRepository.class); mail = mock(EmailService.class);
        service = new LoanReminderService(repo, mail, new ObjectMapper());
        when(repo.findEligibleItemsForDate(any())).thenReturn(List.of(new EligibleLoanItemRow(
                1L, 1L, "PM-1", 10L, "Bạn đọc", "reader@example.test", 1L, "Sách", "BC-1",
                date.atTime(17,0).atOffset(ZoneOffset.ofHours(7)))));
        when(repo.claimInitial(any(), any(), anyString(), any(), any(), any())).thenReturn(1L);
    }
    LoanReminderExecutionResult run() { return service.processDailyReminders(date,"MANUAL_TEST_RUN",1L,"Staff"); }
    @Test void concurrentManualAndScheduledRequestsSendOnlyOnce() throws Exception {
        AtomicBoolean claimed = new AtomicBoolean();
        when(repo.claimInitial(any(), any(), anyString(), any(), any(), any()))
                .thenAnswer(i -> claimed.compareAndSet(false,true) ? 1L : null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(this::run);
            var b=pool.submit(() -> service.processDailyReminders(date,"SYSTEM_SCHEDULED",null,"System"));
            assertEquals(1,a.get().sentCount()+b.get().sentCount());
            verify(mail,times(1)).sendRawHtmlEmail(any(),any(),any());
        } finally { pool.shutdownNow(); }
    }
    @Test void blockedClaimDoesNotSendForAnyExistingState() throws Exception {
        when(repo.claimInitial(any(),any(),anyString(),any(),any(),any())).thenReturn(null);
        assertEquals(1,run().skippedCount()); verifyNoInteractions(mail);
    }
    @Test void databaseFailureBeforeSmtpNeverSends() {
        when(repo.claimInitial(any(),any(),anyString(),any(),any(),any())).thenThrow(new DuplicateKeyException("private db detail"));
        assertEquals(1,run().failedCount()); verifyNoInteractions(mail);
    }
    @Test void acceptedSmtpAndDatabaseFailureNeverBecomeRetryable() throws Exception {
        doThrow(new DuplicateKeyException("unique constraint")).when(repo).finishDelivery(eq(1L),eq("SENT"),anyInt(),any(),any());
        assertEquals(0,run().sentCount());
        verify(mail,times(1)).sendRawHtmlEmail(any(),any(),any());
        verify(repo).finishDelivery(1L,"REVIEW_REQUIRED",0,"DB_AFTER_SMTP","MANUAL_TEST_RUN");
        verify(repo,never()).finishDelivery(any(),eq("PENDING_RETRY"),anyInt(),any(),any());
    }
    @Test void connectFailureCanRetry() throws Exception {
        doThrow(new ConnectException()).when(mail).sendRawHtmlEmail(any(),any(),any());
        run(); verify(repo).finishDelivery(1L,"PENDING_RETRY",0,"SMTP_CONNECT","MANUAL_TEST_RUN");
    }
    @Test void timeoutNeedsReview() throws Exception {
        doThrow(new SocketTimeoutException()).when(mail).sendRawHtmlEmail(any(),any(),any());
        run(); verify(repo).finishDelivery(1L,"REVIEW_REQUIRED",0,"SMTP_UNKNOWN","MANUAL_TEST_RUN");
    }
    @Test void missingConfigurationDoesNotRetryOrReportSent() throws Exception {
        doThrow(new MailPreparationException("SMTP missing")).when(mail).sendRawHtmlEmail(any(),any(),any());
        assertEquals(0,run().sentCount());
        verify(repo).finishDelivery(1L,"FAILED",0,"SMTP_CONFIG","MANUAL_TEST_RUN");
    }
    @Test void simulationCannotCreateLiveRetry() {
        service.processDailyReminders(date,"MANUAL_TEST_RUN",1L,"Staff",true);
        verifyNoInteractions(mail);
        verify(repo,never()).claimInitial(any(),any(),any(),any(),any(),any());
        verify(repo).insertLog(any(),any(),any(),any(),any(),any(),anyInt(),anyLong(),anyInt(),anyInt(),
                any(),any(),any(),any(),any(),eq("FAILED"),eq("SIMULATION"),any(),any(),any(),eq(true));
    }
    LoanReminderLogResponse pending(int retries) {
        return new LoanReminderLogResponse(1L,date,10L,"Reader",null,"reader@example.test","DUE_TODAY",
                1,0,retries,3,"PM-1","Sách","[]","Subject","HTML","PENDING_RETRY",null,"SYSTEM",null,
                false,OffsetDateTime.now(),List.of());
    }
    @Test void concurrentRetriesHaveSingleOwner() throws Exception {
        when(repo.findLogById(1L)).thenReturn(Optional.of(pending(0)));
        AtomicBoolean lock=new AtomicBoolean();
        when(repo.claimRetry(1L,0)).thenAnswer(i->lock.compareAndSet(false,true));
        when(repo.retryItemsStillEligible(1L)).thenReturn(true);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.processPendingRetries(1L,false,1L,"A"));
            var b=pool.submit(()->service.processPendingRetries(1L,false,null,null));
            assertEquals(1,a.get().succeededCount()+b.get().succeededCount());
            verify(mail,times(1)).sendRawHtmlEmail(any(),any(),any());
        } finally {pool.shutdownNow();}
    }
    @Test void retryStopsWhenLoanIsResolvedOrReminderIsStale() {
        when(repo.findLogById(1L)).thenReturn(Optional.of(pending(0)));
        when(repo.claimRetry(1L,0)).thenReturn(true);
        service.processPendingRetries(1L,false,null,null);
        verifyNoInteractions(mail);
        verify(repo).finishDelivery(1L,"SKIPPED",0,"NO_LONGER_ELIGIBLE","RETRY");
    }
    @Test void exhaustedRetryStops() throws Exception {
        when(repo.findLogById(1L)).thenReturn(Optional.of(pending(2)));
        when(repo.claimRetry(1L,2)).thenReturn(true);
        when(repo.retryItemsStillEligible(1L)).thenReturn(true);
        doThrow(new ConnectException()).when(mail).sendRawHtmlEmail(any(),any(),any());
        assertEquals(1,service.processPendingRetries(1L,false,null,null).maxRetriesReachedCount());
        verify(repo).finishDelivery(1L,"FAILED_MAX_RETRIES",3,"SMTP_CONNECT","AUTO_RETRY");
    }
}
