package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.History;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.Event;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.ReservationState;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.ConnectException;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadyPickupNotificationServiceTest {
    private ReadyPickupNotificationRepository repository;
    private EmailService emails;
    private ReadyPickupNotificationService service;
    private MutableClock clock;
    private Event event;
    private final OffsetDateTime occurred=OffsetDateTime.parse("2026-10-14T09:00:00+07:00");
    @BeforeEach void setup() {
        repository=mock(ReadyPickupNotificationRepository.class);emails=mock(EmailService.class);
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(a -> new SimpleTransactionStatus());
        clock=new MutableClock(occurred.toInstant());
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"Quầy nhận sách số 1",clock);
        event=event("reader@example.com",occurred.plusMinutes(5),OffsetDateTime.parse("2026-10-15T17:00:00+07:00"));
        when(repository.lockNext(isNull(),any())).thenAnswer(a->Optional.of(event));
        when(repository.lockClaim(1L)).thenReturn(true);
        when(repository.lockReservation(20L)).thenAnswer(a->Optional.of(new ReservationState("READY_FOR_PICKUP",event.pickupDeadline(),false)));
        when(repository.isLatest(any())).thenReturn(true);
    }
    private Event event(String email,OffsetDateTime sendBefore,OffsetDateTime deadline) {
        return new Event(1,20,email,"Bạn đọc A","Lập trình Java",deadline,null,occurred,sendBefore,"PENDING",0);
    }
    private void verifyOutcome(String status,String code) {
        verify(repository).complete(eq(event),eq(status),eq(code),anyOrNull(),any(),any(),any(),anyBoolean(),nullable(Long.class));
    }
    private static String anyOrNull() { return nullable(String.class); }
    @Test void sendsImmediatelyWithVietnamDeadlineAndFullContent() throws Exception {
        assertTrue(service.processOne(null,false,null));
        verify(emails).sendRawHtmlEmailStrict(eq("reader@example.com"),contains("#20"),
                argThat(html->html.contains("Lập trình Java") && html.contains("17:00 ngày 15/10/2026")
                        && html.contains("Quầy nhận sách số 1") && html.contains("UTC+07:00")),eq(300000L));
        verifyOutcome("SENT",null);
        var order=inOrder(repository,emails);
        order.verify(repository).claim(eq(event),any(),eq(false),isNull());
        order.verify(repository).lockClaim(1L);
        order.verify(repository).lockReservation(20L);
        order.verify(repository).saveContent(eq(1L),anyString(),anyString(),anyString());
        order.verify(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","FULFILLED","CANCELLED","EXPIRED"})
    void neverSendsForOrdersThatAreNoLongerReady(String status) {
        when(repository.lockReservation(20L)).thenReturn(Optional.of(new ReservationState(status,event.pickupDeadline(),false)));
        service.processOne(null,false,null);verifyNoInteractions(emails);verifyOutcome("SKIPPED","RESERVATION_NOT_READY");
    }
    @Test void convertedLegacyOrderIsSkippedEvenIfItsStatusIsReady() {
        when(repository.lockReservation(20L)).thenReturn(Optional.of(new ReservationState("READY_FOR_PICKUP",event.pickupDeadline(),true)));
        service.processOne(null,false,null);verifyNoInteractions(emails);verifyOutcome("SKIPPED","RESERVATION_NOT_READY");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","invalid","a@b","A <a@example.com>","a@example.com\nBcc:x@example.com"})
    void missingOrInvalidEmailDoesNotSendOrRetry(String value) {
        event=event(value,occurred.plusMinutes(5),event.pickupDeadline());
        service.processOne(null,false,null);verifyNoInteractions(emails);verifyOutcome("SKIPPED","INVALID_EMAIL");
    }
    @Test void missingPickupLocationDoesNotInventLocationOrChangeReservation() {
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service=new ReadyPickupNotificationService(repository,emails,manager,Runnable::run,"",clock);
        service.processOne(null,false,null);verifyNoInteractions(emails);verifyOutcome("FAILED","MISSING_PICKUP_LOCATION");
    }
    @Test void missingDeadlineAndExpiredPickupAreNotSent() {
        event=event("reader@example.com",occurred.plusMinutes(5),null);
        service.processOne(null,false,null);verifyOutcome("FAILED","MISSING_PICKUP_DEADLINE");verifyNoInteractions(emails);
    }
    @Test void expiredPickupIsNotSent() {
        event=event("reader@example.com",occurred.plusMinutes(5),occurred.minusSeconds(1));
        service.processOne(null,false,null);verifyOutcome("SKIPPED","PICKUP_EXPIRED");verifyNoInteractions(emails);
    }
    @Test void temporaryFailureSchedulesRetryWithinOriginalWindowThenSucceeds() throws Exception {
        doThrow(new MailSendException("connection failed",new ConnectException())).doNothing()
                .when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("RETRY","SMTP_TEMPORARY_FAILURE");
        clock.advance(Duration.ofSeconds(5));
        event=new Event(1,20,event.recipientEmail(),event.readerName(),event.bookTitle(),event.pickupDeadline(),null,
                occurred,occurred.plusMinutes(5),"RETRY",1);
        service.processOne(null,false,null);verifyOutcome("SENT",null);
        verify(repository).complete(eq(event),eq("SENT"),isNull(),isNull(),any(),
                argThat(sent->Duration.between(occurred,sent).toMillis()<=300000),any(),eq(false),isNull());
    }
    @Test void fiveMinuteWindowIsNeverResetAndNoAttemptStartsAfterIt() {
        clock.advance(Duration.ofMinutes(5));
        service.processOne(null,false,null);verifyNoInteractions(emails);verifyOutcome("FAILED","SEND_WINDOW_EXPIRED");
    }
    @Test void retryIsFinalFailureWhenNoFurtherAttemptFitsInWindow() throws Exception {
        clock.advance(Duration.ofSeconds(296));
        doThrow(new MailSendException("connection failed",new ConnectException())).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","SEND_WINDOW_EXPIRED");
    }
    @Test void missingSmtpDoesNotBecomeSent() throws Exception {
        doThrow(new MailPreparationException("missing")).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","SMTP_NOT_CONFIGURED");
    }
    @Test void badSmtpCredentialsArePermanentFailure() throws Exception {
        doThrow(new MailAuthenticationException("bad auth")).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","SMTP_AUTH_FAILED");
    }
    @ParameterizedTest @ValueSource(ints={450,550})
    void smtpSendReplyControlsRetryWithoutCompileTimeProviderDependency(int reply) throws Exception {
        Class<?> type=Class.forName("org.eclipse.angus.mail.smtp.SMTPSendFailedException");
        Exception failure=(Exception)type.getConstructor(String.class,int.class,String.class,Exception.class,
                jakarta.mail.Address[].class,jakarta.mail.Address[].class,jakarta.mail.Address[].class)
                .newInstance("DATA",reply,"SMTP response",null,null,null,null);
        assertSmtpOutcome(failure,reply);
    }
    @ParameterizedTest @ValueSource(ints={450,550})
    void chainedSmtpAddressReplyControlsRetryWithoutCompileTimeProviderDependency(int reply) throws Exception {
        Class<?> type=Class.forName("org.eclipse.angus.mail.smtp.SMTPAddressFailedException");
        Exception failure=(Exception)type.getConstructor(jakarta.mail.internet.InternetAddress.class,
                String.class,int.class,String.class).newInstance(
                new jakarta.mail.internet.InternetAddress("reader@example.com"),"RCPT TO",reply,"SMTP response");
        jakarta.mail.MessagingException wrapper=new jakarta.mail.MessagingException("nested SMTP failure");
        wrapper.setNextException(failure);
        assertSmtpOutcome(wrapper,reply);
    }
    private void assertSmtpOutcome(Exception failure,int reply) throws Exception {
        doThrow(new MailSendException("send failed",null,Map.of(new Object(),failure))).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);
        verifyOutcome(reply<500?"RETRY":"FAILED",reply<500?"SMTP_TEMPORARY_FAILURE":"SMTP_REJECTED");
    }
    @Test void providerWithoutReturnCodeDoesNotRetryUncertainDelivery() throws Exception {
        doThrow(new MailSendException("send failed",null,Map.of(new Object(),
                new jakarta.mail.SendFailedException("no SMTP reply available")))).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","DELIVERY_UNCERTAIN");
    }
    @Test void uncertainDeliveryDoesNotRetryAndLateAcceptanceDoesNotClaimSlaSuccess() throws Exception {
        doThrow(new java.io.IOException("ack lost")).when(emails).sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","DELIVERY_UNCERTAIN");
    }
    @Test void lateAcceptanceIsRecordedAsFailureWithoutResending() throws Exception {
        doAnswer(a->{clock.advance(Duration.ofSeconds(301));return null;}).when(emails)
                .sendRawHtmlEmailStrict(anyString(),anyString(),anyString(),anyLong());
        service.processOne(null,false,null);verifyOutcome("FAILED","SMTP_ACCEPTED_LATE");
    }
    @Test void simulationRecordsTemporaryFailureAndStaffIdentityWithoutSmtp() {
        service.processOne(null,true,7L);verifyNoInteractions(emails);
        verify(repository).complete(eq(event),eq("RETRY"),eq("SIMULATED_FAILURE"),anyString(),any(),any(),
                eq(occurred.plusSeconds(5)),eq(true),eq(7L));
    }
    @Test void repeatedCallAndConcurrentClaimCannotResendAlreadyClaimedOrSentEvent() {
        when(repository.lockNext(isNull(),any())).thenReturn(Optional.empty());
        assertFalse(service.processOne(null,false,null));assertFalse(service.processOne(null,false,null));verifyNoInteractions(emails);
    }
    @Test void staleEventIsSkippedAndMissingClaimCannotSend() {
        when(repository.isLatest(event)).thenReturn(false);service.processOne(null,false,null);
        verifyOutcome("SKIPPED","RESERVATION_NOT_READY");verifyNoInteractions(emails);
    }
    @Test void failedClaimCannotContactSmtp() {
        when(repository.lockClaim(1L)).thenReturn(false);service.processOne(null,false,null);verifyNoInteractions(emails);
    }
    @Test void triggerRequiresAnActualReadyOrderAndRejectsInvalidId() {
        assertThrows(ApiException.class,()->service.trigger(0,false,7L));
        when(repository.reservationStatus(20L)).thenReturn(Optional.of("CANCELLED"));
        when(repository.history(20L)).thenReturn(List.of());
        assertThrows(ApiException.class,()->service.trigger(20,false,7L));verifyNoInteractions(emails);
    }
    @Test void historyPreservesTypeTimezoneAndCurrentLocation() {
        when(repository.reservationStatus(20L)).thenReturn(Optional.of("READY_FOR_PICKUP"));when(repository.history(20L)).thenReturn(List.of());
        History history=service.history(20);assertEquals("Asia/Ho_Chi_Minh",history.timeZone());assertEquals("Quầy nhận sách số 1",history.pickupLocation());
    }
    @Test void emailContentEscapesUserControlledHtml() {
        Event malicious=new Event(1,20,"a@example.com","<script>x</script>","Java & <b>SQL</b>",
                event.pickupDeadline(),null,occurred,occurred.plusMinutes(5),"PENDING",0);
        String html=ReadyPickupNotificationService.content(malicious,"Quầy <1>");
        assertFalse(html.contains("<script>"));assertTrue(html.contains("Java &amp; &lt;b&gt;SQL&lt;/b&gt;"));
        assertTrue(html.contains("Quầy &lt;1&gt;"));
    }
    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant){this.instant=instant;}
        void advance(Duration duration){instant=instant.plus(duration);}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return instant;}
    }
}
