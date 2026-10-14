package com.duanttcsn5.library;

import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.*;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.exception.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupCancellationEmailServiceTest {
    private ReadyPickupNotificationRepository repository;
    private EmailService emails;
    private ReadyPickupNotificationService service;
    private Event event;
    private final OffsetDateTime point=OffsetDateTime.parse("2026-10-15T17:02:00+07:00");
    private final OffsetDateTime deadline=point.minusMinutes(2), cancelled=point.minusMinutes(1);
    private static final String REASON=ReservationAutoCancellationService.OVERDUE_CANCELLATION_REASON;
    @BeforeEach void setup() {
        repository=mock(ReadyPickupNotificationRepository.class);emails=mock(EmailService.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(a->new SimpleTransactionStatus());
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"",Clock.fixed(point.toInstant(),ZoneOffset.UTC));
        event=event(0,"a@example.com");
        when(repository.lockNext(isNull(),any())).thenAnswer(a->Optional.of(event));
        when(repository.lockClaim(1)).thenReturn(true);
        when(repository.lockReservation(20)).thenReturn(Optional.of(state("CANCELLED",REASON,null,"Hệ thống",false,cancelled)));
    }
    private Event event(int attempts,String email) {
        return new Event(1,20,email,"Bạn đọc A","Mạng máy tính",deadline,null,point,null,"PENDING",attempts,
                ReadyPickupNotificationService.CANCELLATION_TYPE,9L,REASON,cancelled);
    }
    private ReservationState state(String status,String reason,Long actor,String name,boolean converted,OffsetDateTime time) {
        return new ReservationState(status,deadline,converted,reason,time,actor,name);
    }
    private void outcome(String status,String code) {
        verify(repository).complete(eq(event),eq(status),eq(code),nullable(String.class),any(),any(),any(),anyBoolean(),nullable(Long.class));
    }
    @Test void sendsCancellationAfterExpiryWithDetailsAndExistingRebookingLinkWithoutPickupDesk() throws Exception {
        assertTrue(service.processOne(null,false,null));
        verify(emails).sendRawHtmlEmailStrict(eq("a@example.com"),contains("#20"),argThat(html->
                html.contains("Mạng máy tính") && html.contains("17:00 ngày 15/10/2026")
                && html.contains("17:01 ngày 15/10/2026") && html.contains("Đã hủy")
                && html.contains("http://localhost:5173/catalog/books/9#reserve-book")
                && html.contains("cuối hàng đợi") && html.contains("không bị khóa")),eq(30000L));
        outcome("SENT",null);
    }
    @ParameterizedTest @ValueSource(strings={"READY_FOR_PICKUP","PENDING","FULFILLED","EXPIRED"})
    void expiryAloneOrReceivedOrderDoesNotPermitEmail(String status) {
        when(repository.lockReservation(20)).thenReturn(Optional.of(state(status,REASON,null,"Hệ thống",false,cancelled)));
        service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","CANCELLATION_EVENT_CHANGED");
    }
    @ParameterizedTest @ValueSource(strings={"Bạn đọc tự huỷ đơn.","Sách bị mất", "Quá hạn nhận"})
    void otherReasonsNeverSendExpiryEmail(String reason) {
        when(repository.lockReservation(20)).thenReturn(Optional.of(state("CANCELLED",reason,null,"Hệ thống",false,cancelled)));
        service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","CANCELLATION_EVENT_CHANGED");
    }
    @Test void manualCancellationWithIdenticalTextDoesNotSend() {
        when(repository.lockReservation(20)).thenReturn(Optional.of(state("CANCELLED",REASON,7L,"Thủ thư",false,cancelled)));
        service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","CANCELLATION_EVENT_CHANGED");
    }
    @Test void receivedLegacyOrderAndChangedCancellationEventAreSkipped() {
        when(repository.lockReservation(20)).thenReturn(Optional.of(state("CANCELLED",REASON,null,"Hệ thống",true,cancelled)));
        service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","CANCELLATION_EVENT_CHANGED");
    }
    @Test void differentCancellationTimestampIsSkipped() {
        when(repository.lockReservation(20)).thenReturn(Optional.of(state("CANCELLED",REASON,null,"Hệ thống",false,cancelled.plusSeconds(1))));
        service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","CANCELLATION_EVENT_CHANGED");
    }
    @ParameterizedTest @ValueSource(strings={"","invalid","a@b"})
    void invalidEmailIsRecordedWithoutSmtp(String email) {
        event=event(0,email);service.processOne(null,false,null);verifyNoInteractions(emails);outcome("SKIPPED","INVALID_EMAIL");
    }
    @Test void transientConnectionFailureRetriesAtFiveThenTenSecondsAndStopsAtThreeTotalAttempts() throws Exception {
        doThrow(new MailSendException("connection",new java.net.ConnectException())).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        for (int count=0;count<3;count++) {
            event=event(count,"a@example.com");service.processOne(null,false,null);
            if (count<2) verify(repository).complete(eq(event),eq("RETRY"),eq("SMTP_TEMPORARY_FAILURE"),anyString(),any(),
                    eq(point),eq(point.plusSeconds(count==0?5:10)),eq(false),isNull());
            else outcome("FAILED","SMTP_RETRY_EXHAUSTED");
        }
        verify(emails,times(3)).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
    }
    @Test void retrySuccessCompletesExistingEvent() throws Exception {
        event=event(1,"a@example.com");service.processOne(null,false,null);outcome("SENT",null);
    }
    @Test void uncertainSmtpOutcomeIsNotRetried() throws Exception {
        doThrow(new java.io.IOException("ack lost")).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);outcome("FAILED","DELIVERY_UNCERTAIN");
    }
    @Test void smtpConfigurationFailureDoesNotUndoCancellation() throws Exception {
        doThrow(new MailPreparationException("missing")).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);outcome("FAILED","SMTP_NOT_CONFIGURED");
    }
    @Test void alreadyClaimedOrSentEventCannotResend() {
        when(repository.lockNext(isNull(),any())).thenReturn(Optional.empty());
        assertFalse(service.processOne(null,false,null));assertFalse(service.processOne(null,false,null));verifyNoInteractions(emails);
    }
    private void candidate(String status,boolean converted,OffsetDateTime due) {
        when(repository.cancellationCandidate(20)).thenReturn(Optional.of(new CancellationCandidate(20,9,"Mạng máy tính",
                "Bạn đọc A","a@example.com",status,due,converted)));
    }
    @Test void futurePreviewIsReadOnlyAndNeverAllowsRealCancellation() {
        candidate("READY_FOR_PICKUP",false,point.plusDays(1));
        var preview=service.previewCancellation(20,point.plusDays(1).plusSeconds(1));
        assertTrue(preview.eligible());assertFalse(preview.canRunNow());assertTrue(preview.reason().contains("Mốc tương lai"));
        verifyNoInteractions(emails);verify(repository,never()).claim(any(),any(),anyBoolean(),any());
    }
    @Test void actualExpiredReadyOrderAllowsExplicitConfirmation() {
        candidate("READY_FOR_PICKUP",false,deadline);assertTrue(service.previewCancellation(20,null).canRunNow());verifyNoInteractions(emails);
    }
    @Test void deadlineEqualityDoesNotCountAsOverdue() {
        candidate("READY_FOR_PICKUP",false,point);assertFalse(service.previewCancellation(20,point).eligible());
    }
    @Test void missingDeadlineAndReceivedOrderCannotRun() {
        candidate("READY_FOR_PICKUP",false,null);assertFalse(service.previewCancellation(20,null).canRunNow());
        candidate("FULFILLED",true,deadline);assertFalse(service.previewCancellation(20,null).canRunNow());
    }
    @Test void contentEscapesNamesAndTitles() {
        String html=service.cancellationContent("<script>x</script>",20,"Mạng & <b>máy</b>",deadline,cancelled,9L);
        assertFalse(html.contains("<script>"));assertTrue(html.contains("Mạng &amp; &lt;b&gt;máy&lt;/b&gt;"));
    }
    @Test void previewRejectsInvalidIdAndMissingOrder() {
        assertThrows(ApiException.class,()->service.previewCancellation(0,null));
        when(repository.cancellationCandidate(20)).thenReturn(Optional.empty());
        assertThrows(ApiException.class,()->service.previewCancellation(20,null));
    }
}
