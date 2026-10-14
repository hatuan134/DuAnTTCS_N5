package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.Notification;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.*;
import com.duanttcsn5.library.service.EmailService;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PickupLastDayReminderServiceTest {
    private static final LocalDate DAY=LocalDate.of(2026,10,15);
    private FakeRepository repository;
    private CaptureEmail emails;
    private MutableClock clock;
    private ReadyPickupNotificationService service;
    @BeforeEach void setup() {
        repository=new FakeRepository();emails=new CaptureEmail();clock=new MutableClock("2026-10-15T07:00:00+07:00");
        service=new ReadyPickupNotificationService(repository,emails,new Transactions(),Runnable::run,"Quầy số 1",clock);
        repository.rows.add(row(20,"READY_FOR_PICKUP","2026-10-15T17:00:00+07:00",false));
    }
    @Test void todaySendsCompleteVietnamEmailAndRecordsHistory() {
        var report=service.runLastDayToday(DAY,7L);
        assertEquals(1,report.queuedCount());assertEquals(1,emails.contents.size());
        String html=emails.contents.get(0);
        for(String text:List.of("Hôm nay là ngày cuối cùng nhận sách","Cơ sở dữ liệu","17:00 ngày 15/10/2026","Quầy số 1","UTC+07:00")) assertTrue(html.contains(text));
        var history=repository.history(20).get(0);
        assertEquals("LAST_DAY_REMINDER",history.notificationType());assertEquals("SENT",history.status());
        assertEquals("reader20@example.com",history.recipientEmail());assertNotNull(history.sentAt());
        assertEquals(7L,repository.lastActor);
    }
    @Test void tomorrowAndYesterdayAreExcluded() {
        repository.rows= new ArrayList<>(List.of(row(1,"READY_FOR_PICKUP","2026-10-14T17:00:00+07:00",false),row(2,"READY_FOR_PICKUP","2026-10-16T17:00:00+07:00",false)));
        assertTrue(service.runLastDayToday(DAY,null).entries().isEmpty());assertTrue(emails.contents.isEmpty());
    }
    @Test void receivedCancelledAndConvertedOrdersAreExcluded() {
        repository.rows=new ArrayList<>(List.of(row(1,"FULFILLED","2026-10-15T17:00:00+07:00",false),row(2,"CANCELLED","2026-10-15T17:00:00+07:00",false),row(3,"READY_FOR_PICKUP","2026-10-15T17:00:00+07:00",true)));
        assertEquals(0,service.runLastDayToday(DAY,null).queuedCount());assertTrue(emails.contents.isEmpty());
    }
    @Test void expiryBeforeOrExactlyAtMorningScheduleIsExcluded() {
        repository.rows=new ArrayList<>(List.of(row(1,"READY_FOR_PICKUP","2026-10-15T06:59:59+07:00",false),row(2,"READY_FOR_PICKUP","2026-10-15T07:00:00+07:00",false)));
        assertEquals(0,service.runLastDayToday(DAY,null).queuedCount());assertTrue(emails.contents.isEmpty());
    }
    @Test void changedDeadlineBeforeScheduleUsesCurrentDayOnly() {
        repository.rows.set(0,row(20,"READY_FOR_PICKUP","2026-10-16T17:00:00+07:00",false));
        assertTrue(service.runLastDayToday(DAY,null).entries().isEmpty());
    }
    @Test void changedDeadlineImmediatelyBeforeSmtpIsSkipped() {
        repository.beforeLock=()->repository.rows.set(0,row(20,"READY_FOR_PICKUP","2026-10-16T17:00:00+07:00",false));
        service.runLastDayToday(DAY,null);assertTrue(emails.contents.isEmpty());assertEquals("PICKUP_DEADLINE_CHANGED",repository.lastCode);
    }
    @Test void cancelledImmediatelyBeforeSmtpIsSkipped() {
        repository.beforeLock=()->repository.rows.set(0,row(20,"CANCELLED","2026-10-15T17:00:00+07:00",false));
        service.runLastDayToday(DAY,null);assertTrue(emails.contents.isEmpty());assertEquals("RESERVATION_NOT_READY",repository.lastCode);
    }
    @Test void receivedImmediatelyBeforeSmtpIsSkipped() {
        repository.beforeLock=()->repository.rows.set(0,row(20,"FULFILLED","2026-10-15T17:00:00+07:00",false));
        service.runLastDayToday(DAY,null);assertTrue(emails.contents.isEmpty());assertEquals("RESERVATION_NOT_READY",repository.lastCode);
    }
    @Test void deadlinePassingBeforeSmtpIsSkipped() {
        repository.beforeLock=()->clock.set("2026-10-15T17:00:00+07:00");
        service.runLastDayToday(DAY,null);assertTrue(emails.contents.isEmpty());assertEquals("PICKUP_EXPIRED",repository.lastCode);
    }
    @Test void multipleOrdersAndReadersEachReceiveOneReminder() {
        repository.rows.add(row(21,"READY_FOR_PICKUP","2026-10-15T18:00:00+07:00",false));
        repository.rows.add(new ReminderCandidate(22,"READY_FOR_PICKUP","reader20@example.com","Bạn đọc 20","Sách khác",OffsetDateTime.parse("2026-10-15T19:00:00+07:00"),false,null));
        assertEquals(3,service.runLastDayToday(DAY,null).queuedCount());assertEquals(3,emails.contents.size());
        assertEquals(0,service.runLastDayToday(DAY,null).queuedCount());assertEquals(3,emails.contents.size());
    }
    @Test void repeatedRunsDoNotResendForSameOrderAndDeadline() {
        service.runLastDayToday(DAY,null);service.runLastDayToday(DAY,null);service.runLastDayToday(DAY,null);
        assertEquals(1,emails.contents.size());assertEquals(1,repository.events.size());
    }
    @Test void aDifferentDeadlineHasADifferentDeduplicationKey() {
        service.runLastDayToday(DAY,null);
        repository.rows.set(0,row(20,"READY_FOR_PICKUP","2026-10-15T18:00:00+07:00",false));
        service.runLastDayToday(DAY,null);assertEquals(2,emails.contents.size());assertEquals(2,repository.events.size());
    }
    @Test void previewAnyDateNeverEnqueuesSendsOrRecordsHistory() {
        var report=service.previewLastDay(DAY);assertTrue(report.preview());assertEquals(1,report.eligibleCount());
        service.previewLastDay(DAY.minusDays(10));service.previewLastDay(DAY.plusDays(10));
        assertTrue(repository.events.isEmpty());assertTrue(emails.contents.isEmpty());
        assertEquals(OffsetDateTime.parse("2026-10-15T07:00:00+07:00"),report.evaluatedAt());
    }
    @Test void liveSendingRejectsFuturePastAndNullDate() {
        for(LocalDate day:List.of(DAY.minusDays(1),DAY.plusDays(1))) assertEquals("REMINDER_DATE_NOT_TODAY",assertThrows(ApiException.class,()->service.runLastDayToday(day,null)).getCode());
        assertThrows(ApiException.class,()->service.previewLastDay(null));assertThrows(ApiException.class,()->service.runLastDayToday(null,null));assertTrue(emails.contents.isEmpty());
    }
    @Test void rejectsLiveSendingBeforeSevenAndCronHasExactMorningTime() {
        clock.set("2026-10-15T06:59:59+07:00");assertEquals("BEFORE_REMINDER_TIME",assertThrows(ApiException.class,()->service.runLastDayToday(DAY,null)).getCode());
        assertEquals("0 0 7 * * *",service.lastDayCron());assertTrue(repository.events.isEmpty());
    }
    @Test void missingEmailIsRecordedAsSkippedWithoutSending() {
        var r=repository.rows.get(0);repository.rows.set(0,new ReminderCandidate(r.reservationId(),r.status(),"invalid",r.readerName(),r.bookTitle(),r.pickupDeadline(),false,null));
        service.runLastDayToday(DAY,null);assertEquals("INVALID_EMAIL",repository.lastCode);assertTrue(emails.contents.isEmpty());
    }
    @Test void missingLocationIsRecordedAsFailedWithoutSending() {
        service=new ReadyPickupNotificationService(repository,emails,new Transactions(),Runnable::run,"",clock);
        service.runLastDayToday(DAY,null);assertEquals("MISSING_PICKUP_LOCATION",repository.lastCode);assertTrue(emails.contents.isEmpty());
    }
    @Test void missingSmtpDoesNotBecomeSuccessfulOrResend() {
        emails.failure=new MailPreparationException("Missing SMTP");service.runLastDayToday(DAY,null);
        assertEquals("SMTP_NOT_CONFIGURED",repository.lastCode);assertEquals("FAILED",repository.history(20).get(0).status());
        service.runLastDayToday(DAY,null);assertEquals(1,emails.calls);
    }
    @Test void uncertainSmtpDeliveryNeverResends() {
        emails.failure=new java.io.IOException("Unknown SMTP result");service.runLastDayToday(DAY,null);
        assertEquals("DELIVERY_UNCERTAIN",repository.lastCode);service.runLastDayToday(DAY,null);assertEquals(1,emails.calls);
    }
    @Test void temporaryConnectionFailureRetriesSameEventWithABound() {
        emails.failure=new MailSendException("Connection",new java.net.ConnectException());service.runLastDayToday(DAY,null);
        assertEquals("RETRY",repository.history(20).get(0).status());emails.failure=null;clock.set("2026-10-15T07:00:06+07:00");
        service.runLastDayToday(DAY,null);assertEquals(1,repository.events.size());assertEquals(1,emails.contents.size());assertEquals("SENT",repository.history(20).get(0).status());
    }
    @Test void claimIsCommittedBeforeSmtpAndCannotBeClaimedTwice() {
        service.runLastDayToday(DAY,null);assertEquals(1,repository.claims);assertFalse(service.processLastDayOne(20L,null));
        assertEquals(1,emails.calls);
    }
    @Test void timezoneBoundarySelectsVietnamDay() {
        repository.rows.set(0,row(20,"READY_FOR_PICKUP","2026-10-15T10:00:00Z",false));
        var report=service.runLastDayToday(DAY,null);assertEquals(1,report.queuedCount());assertTrue(emails.contents.get(0).contains("17:00 ngày 15/10/2026"));
    }
    @Test void emailEscapesReaderBookAndLocationHtml() {
        String html=ReadyPickupNotificationService.lastDayContent("<script>x</script>",20,"SQL & <b>Java</b>",repository.rows.get(0).pickupDeadline(),"Quầy <1>");
        assertFalse(html.contains("<script>"));assertTrue(html.contains("SQL &amp; &lt;b&gt;Java&lt;/b&gt;"));assertTrue(html.contains("Quầy &lt;1&gt;"));
    }
    @Test void temporaryFailureStopsAfterThreeAttempts() {
        emails.failure=new MailSendException("Connection",new java.net.ConnectException());
        service.runLastDayToday(DAY,null);clock.set("2026-10-15T07:00:06+07:00");service.runLastDayToday(DAY,null);
        clock.set("2026-10-15T07:00:17+07:00");service.runLastDayToday(DAY,null);
        assertEquals("SMTP_RETRY_EXHAUSTED",repository.lastCode);assertEquals(3,emails.calls);
        clock.set("2026-10-15T07:01:00+07:00");service.runLastDayToday(DAY,null);assertEquals(3,emails.calls);
    }
    @Test void configuredTimeDrivesCronPreviewAndLiveGuard() throws Exception {
        var field=ReadyPickupNotificationService.class.getDeclaredField("lastDaySendTime");field.setAccessible(true);field.set(service,"08:15");
        assertEquals("0 15 8 * * *",service.lastDayCron());assertEquals(8,service.previewLastDay(DAY).evaluatedAt().getHour());
        assertEquals(15,service.previewLastDay(DAY).evaluatedAt().getMinute());
        assertThrows(ApiException.class,()->service.runLastDayToday(DAY,null));
    }
    @Test void cronNextExecutionIsExactlySevenVietnamTime() {
        var cron=org.springframework.scheduling.support.CronExpression.parse(service.lastDayCron());
        var before=OffsetDateTime.parse("2026-10-15T06:59:59+07:00").atZoneSameInstant(ReadyPickupNotificationService.ZONE);
        assertEquals(OffsetDateTime.parse("2026-10-15T07:00:00+07:00").toInstant(),cron.next(before).toInstant());
    }
    @Test void schedulerAndManualRunShareSameDeduplicationKey() {
        service.scheduleLastDayReminders();service.scheduleLastDayReminders();service.runLastDayToday(DAY,null);
        assertEquals(1,repository.events.size());assertEquals(1,emails.calls);
    }
    @Test void smtpAcceptanceAtDeadlineIsLateAndNeverResent() {
        emails.beforeAccept=()->clock.set("2026-10-15T17:00:00+07:00");
        service.runLastDayToday(DAY,null);assertEquals("SMTP_ACCEPTED_LATE",repository.lastCode);
        service.runLastDayToday(DAY,null);assertEquals(1,emails.calls);
    }
    @Test void liveBatchReturnsBeforeTheBackgroundSmtpWork() {
        List<Runnable> tasks=new ArrayList<>();
        service=new ReadyPickupNotificationService(repository,emails,new Transactions(),tasks::add,"Quầy số 1",clock);
        var report=service.runLastDayToday(DAY,7L);assertEquals(1,report.queuedCount());
        assertTrue(emails.contents.isEmpty());assertEquals("PENDING",report.entries().get(0).notificationStatus());
        tasks.forEach(Runnable::run);assertEquals(1,emails.contents.size());assertEquals(7L,repository.lastActor);
    }
    @Test void aBusyExecutorDoesNotLoseTheDurableReminder() {
        service=new ReadyPickupNotificationService(repository,emails,new Transactions(),
                task->{throw new java.util.concurrent.RejectedExecutionException();},"Quầy số 1",clock);
        assertEquals(1,service.runLastDayToday(DAY,null).queuedCount());assertTrue(emails.contents.isEmpty());
        assertTrue(service.processLastDayOne(20L,null));assertEquals(1,emails.contents.size());
    }
    @org.springframework.context.annotation.Configuration
    static class SchedulingFixture {
        @org.springframework.context.annotation.Bean
        ReadyPickupNotificationService readyPickupNotificationService() {
            return new ReadyPickupNotificationService(new FakeRepository(),new CaptureEmail(),
                    new Transactions(),Runnable::run,"Quầy số 1",Clock.systemUTC());
        }
    }
    @Test void schedulesRegisterWhenCircularReferencesAreDisabled() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(false);
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test-scheduling",Map.of("app.pickup-email.last-day-send-time","07:00",
                            "app.pickup-email.automatic-enabled","false")));
            context.register(SchedulingFixture.class,
                    com.duanttcsn5.library.config.ReadyPickupNotificationConfig.class,
                    com.duanttcsn5.library.config.SchedulingConfig.class);
            context.refresh();
            assertEquals("0 0 7 * * *",context.getBean("pickupLastDayCron",String.class));
            var scheduling=context.getBean(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class);
            assertEquals(2,scheduling.getScheduledTasks().size());
        }
    }
    private static ReminderCandidate row(long id,String status,String deadline,boolean converted) {
        return new ReminderCandidate(id,status,"reader"+id+"@example.com","Bạn đọc "+id,"Cơ sở dữ liệu",OffsetDateTime.parse(deadline),converted,null);
    }
    static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(String value){set(value);} void set(String value){instant=OffsetDateTime.parse(value).toInstant();}
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return instant;}
    }
    static class Transactions implements PlatformTransactionManager {
        public TransactionStatus getTransaction(TransactionDefinition definition){return new SimpleTransactionStatus();}
        public void commit(TransactionStatus status){} public void rollback(TransactionStatus status){}
    }
    static class CaptureEmail extends EmailService {
        final List<String> contents=new ArrayList<>();int calls;Exception failure;Runnable beforeAccept;
        CaptureEmail(){super(null,Runnable::run,"http://localhost:5173","noreply@example.com",3,0);}
        @Override public void sendRawHtmlEmailStrict(String to,String subject,String html,long remaining) throws Exception {
            calls++;if(failure!=null)throw failure;if(beforeAccept!=null)beforeAccept.run();contents.add(html);
        }
    }
    static class FakeRepository extends ReadyPickupNotificationRepository {
        List<ReminderCandidate> rows=new ArrayList<>();final Map<String,Event> events=new LinkedHashMap<>();
        final Map<Long,Event> claimed=new HashMap<>();final Map<Long,OffsetDateTime> next=new HashMap<>();
        final Map<Long,String> content=new HashMap<>();String lastCode;Long lastActor;int claims;Runnable beforeLock;
        FakeRepository(){super(null);}
        private String key(long id,OffsetDateTime deadline){return id+"/"+deadline.toInstant();}
        @Override public List<ReminderCandidate> reminderCandidates(OffsetDateTime start,OffsetDateTime end) {
            return rows.stream().filter(r->!r.pickupDeadline().isBefore(start)&&r.pickupDeadline().isBefore(end)).map(r->{
                Event event=events.get(key(r.reservationId(),r.pickupDeadline()));
                return new ReminderCandidate(r.reservationId(),r.status(),r.recipientEmail(),r.readerName(),r.bookTitle(),r.pickupDeadline(),r.converted(),event==null?null:event.status());
            }).toList();
        }
        @Override public boolean enqueueReminder(ReminderCandidate r,OffsetDateTime now,String location) {
            String key=key(r.reservationId(),r.pickupDeadline());if(events.containsKey(key))return false;
            Event event=new Event(events.size()+1,r.reservationId(),r.recipientEmail(),r.readerName(),r.bookTitle(),r.pickupDeadline(),location,now,r.pickupDeadline(),"PENDING",0,"LAST_DAY_REMINDER");
            events.put(key,event);next.put(event.id(),now);return true;
        }
        @Override public void failAbandoned(OffsetDateTime now) {}
        @Override public Optional<Event> lockNext(Long id,OffsetDateTime now){return lockNextReminder(id,now);}
        @Override public Optional<Event> lockNextReminder(Long id,OffsetDateTime now) {
            return events.values().stream().filter(e->(id==null||e.reservationId()==id)&&List.of("PENDING","RETRY").contains(e.status())&&!next.get(e.id()).isAfter(now)).findFirst();
        }
        @Override public void claim(Event e,OffsetDateTime started,boolean simulated,Long actor){claims++;lastActor=actor;claimed.put(e.id(),e);replace(e,"SENDING",e.attemptCount()+1);}
        @Override public boolean lockClaim(long id){return claimed.containsKey(id);}
        @Override public Optional<ReservationState> lockReservation(long id) {
            if(beforeLock!=null){Runnable action=beforeLock;beforeLock=null;action.run();}
            return rows.stream().filter(r->r.reservationId()==id).findFirst().map(r->new ReservationState(r.status(),r.pickupDeadline(),r.converted()));
        }
        @Override public void saveContent(long id,String location,String subject,String html){content.put(id,html);}
        @Override public void complete(Event e,String status,String code,String message,OffsetDateTime started,OffsetDateTime finished,OffsetDateTime scheduled,boolean simulated,Long actor){
            lastCode=code;claimed.remove(e.id());replace(e,status,e.attemptCount()+1);next.put(e.id(),scheduled);
        }
        private void replace(Event e,String status,int attempts){events.put(key(e.reservationId(),e.pickupDeadline()),new Event(e.id(),e.reservationId(),e.recipientEmail(),e.readerName(),e.bookTitle(),e.pickupDeadline(),e.pickupLocation(),e.eventAt(),e.sendBefore(),status,attempts,e.notificationType()));}
        @Override public List<Notification> history(long id){return events.values().stream().filter(e->e.reservationId()==id).map(e->new Notification(e.id(),id,e.notificationType(),e.recipientEmail(),e.readerName(),e.bookTitle(),e.pickupDeadline(),e.pickupLocation(),e.eventAt(),e.sendBefore(),"SENT".equals(e.status())?e.eventAt():null,null,e.status(),e.attemptCount(),ReadyPickupNotificationService.lastDaySubject(id),content.get(e.id()),lastCode,null,List.of())).toList();}
    }
}
