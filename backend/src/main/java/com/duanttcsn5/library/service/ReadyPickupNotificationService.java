package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.History;
import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.TriggerResult;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.duanttcsn5.library.dto.book.PickupLastDayReminderDtos.Entry;
import com.duanttcsn5.library.dto.book.PickupLastDayReminderDtos.Report;
import com.duanttcsn5.library.repository.ReadyPickupNotificationRepository.ReminderCandidate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import jakarta.mail.internet.InternetAddress;

@Service
public class ReadyPickupNotificationService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log=LoggerFactory.getLogger(ReadyPickupNotificationService.class);
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private final ReadyPickupNotificationRepository repository;
    private final EmailService emails;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final String pickupLocation;
    private final Clock clock;
    @Value("${app.pickup-email.automatic-enabled:true}")
    private boolean automaticEnabled=true;
    @Value("${app.pickup-email.last-day-send-time:07:00}")
    private String lastDaySendTime="07:00";
    @Value("${app.mail.max-attempts:3}")
    private int reminderMaxAttempts=3;

    @Autowired
    public ReadyPickupNotificationService(ReadyPickupNotificationRepository repository,EmailService emails,
            PlatformTransactionManager manager,@Qualifier("pickupNotificationExecutor") Executor executor,
            @Value("${app.pickup-email.location:}") String pickupLocation) {
        this(repository,emails,manager,executor,pickupLocation,Clock.systemUTC());
    }
    public ReadyPickupNotificationService(ReadyPickupNotificationRepository repository,EmailService emails,
            PlatformTransactionManager manager,Executor executor,String pickupLocation,Clock clock) {
        this.repository=repository;this.emails=emails;this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor=executor;this.pickupLocation=pickupLocation==null?"":pickupLocation.trim();this.clock=clock;
    }
    public String pickupLocation() { return pickupLocation.isBlank()?null:pickupLocation; }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(),ZONE); }

    // Fast path after commit. The database trigger remains the durable source of events.
    public void wakeAfterCommit() {
        if (!automaticEnabled) return;
        if(TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch(); }
            });
        } else dispatch();
    }
    // Separate scheduler: old daily/reminder jobs cannot postpone a pickup event.
    @Scheduled(fixedDelay=1000,initialDelay=1000,scheduler="pickupNotificationScheduler")
    public void dispatch() {
        if (!automaticEnabled) return;
        try { transactions.executeWithoutResult(tx -> repository.failAbandoned(now())); }
        catch (Exception e) { log.error("Không đối soát được lượt gửi email bị gián đoạn.", e); return; }
        dispatchWorkers(false,null,25);
    }
    private void dispatchWorkers(boolean reminderOnly,Long actorId,int limit) {
        for(int i=0;i<4;i++) {
            try { executor.execute(()-> {
                try { for(int n=0;n<limit && (reminderOnly ? processLastDayOne(null,actorId) : processOne(null,false,null));n++) { /* bounded drain */ } }
                catch(Exception e) { log.error("Không xử lý được hàng đợi email nhận sách; sẽ kiểm tra lại.",e); }
            }); } catch(RejectedExecutionException e) { return; }
        }
    }
    public History history(long reservationId) {
        validateId(reservationId);
        String status=repository.reservationStatus(reservationId).orElseThrow(()->new ApiException(
                HttpStatus.NOT_FOUND,"RESERVATION_NOT_FOUND","Không tìm thấy đơn đặt giữ."));
        return new History(reservationId,status,pickupLocation(),ZONE.getId(),repository.history(reservationId));
    }
    public TriggerResult trigger(long reservationId,boolean simulateFailure,Long actorId) {
        History before=history(reservationId);
        if(!"READY_FOR_PICKUP".equals(before.reservationStatus())) throw new ApiException(HttpStatus.CONFLICT,
                "RESERVATION_NOT_READY","Chỉ chạy thử thông báo cho đơn đang Chờ nhận.");
        boolean processed=processOne(reservationId,simulateFailure,actorId);
        String message=processed?(simulateFailure?"Đã mô phỏng lỗi tạm thời. Hệ thống sẽ tự thử lại trong cửa sổ 5 phút.":
                "Đã xử lý lượt gửi email. Xem kết quả trong lịch sử."):
                "Không có email đến lượt gửi: đơn đã gửi, đã kết thúc, đang được xử lý hoặc đang chờ lần thử tiếp theo.";
        History after=history(reservationId);
        if (processed && !after.notifications().isEmpty()) {
            var result=after.notifications().stream()
                    .filter(n -> "READY_FOR_PICKUP".equals(n.notificationType())).findFirst().orElse(null);
            if (result == null) return new TriggerResult(message,after);
            if ("FAILED".equals(result.status()) || "SKIPPED".equals(result.status())) {
                message="Không gửi email: "+result.errorMessage();
            } else if ("SENT".equals(result.status())) {
                message="Đã gửi email nhận sách. Máy chủ SMTP đã chấp nhận thư.";
            }
        }
        return new TriggerResult(message,after);
    }
    public boolean processOne(Long reservationId,boolean simulateFailure,Long actorId) {
        return processOne(reservationId,simulateFailure,actorId,false);
    }
    public boolean processLastDayOne(Long reservationId,Long actorId) {
        return processOne(reservationId,false,actorId,true);
    }
    private boolean processOne(Long reservationId,boolean simulateFailure,Long actorId,boolean reminderOnly) {
        Event event=transactions.execute(tx -> {
            Event pending=(reminderOnly ? repository.lockNextReminder(reservationId,now())
                    : repository.lockNext(reservationId,now())).orElse(null);
            if(pending!=null) repository.claim(pending,now(),simulateFailure,actorId);
            return pending;
        });
        if(event==null)return false;
        transactions.executeWithoutResult(tx -> {
            if(repository.lockClaim(event.id())) attempt(event,simulateFailure,actorId);
        });
        return true;
    }
    private void attempt(Event event,boolean simulateFailure,Long actorId) {
        OffsetDateTime started=now();
        var state=repository.lockReservation(event.reservationId()).orElse(null);
        String location=event.pickupLocation()==null?pickupLocation:event.pickupLocation();
        boolean reminder="LAST_DAY_REMINDER".equals(event.notificationType());
        String subject=reminder ? lastDaySubject(event.reservationId()) : "Sách đã sẵn sàng để nhận — Đơn #"+event.reservationId();
        String html=reminder ? lastDayContent(event.readerName(),event.reservationId(),event.bookTitle(),event.pickupDeadline(),location)
                : content(event,location);
        repository.saveContent(event.id(),location,subject,html);
        if(state==null || !"READY_FOR_PICKUP".equals(state.status()) || state.converted()
                || (!reminder && !repository.isLatest(event))) {
            finish(event,"SKIPPED","RESERVATION_NOT_READY","Đơn đã nhận, đã hủy hoặc lần giữ sách đã thay đổi.",started,false,actorId);return;
        }
        if(state.pickupDeadline()==null || event.pickupDeadline()==null) {
            finish(event,"FAILED","MISSING_PICKUP_DEADLINE","Đơn chưa có hạn cuối nhận sách.",started,false,actorId);return;
        }
        if(reminder && (!state.pickupDeadline().toInstant().equals(event.pickupDeadline().toInstant())
                || !state.pickupDeadline().atZoneSameInstant(ZONE).toLocalDate().equals(now().toLocalDate()))) {
            finish(event,"SKIPPED","PICKUP_DEADLINE_CHANGED","Hạn nhận đã thay đổi hoặc không còn là ngày cuối nhận sách.",started,false,actorId);return;
        }
        if(reminder && now().toLocalTime().isBefore(sendTime())) {
            finish(event,"SKIPPED","BEFORE_REMINDER_TIME","Chưa đến giờ gửi nhắc buổi sáng đã khai báo.",started,false,actorId);return;
        }
        if(now().isAfter(state.pickupDeadline()) || (reminder && !now().isBefore(state.pickupDeadline()))) {
            finish(event,"SKIPPED","PICKUP_EXPIRED","Đơn đã quá hạn nhận sách; không gửi thông báo đến nhận.",started,false,actorId);return;
        }
        if(!now().isBefore(event.sendBefore())) {
            finish(event,"FAILED","SEND_WINDOW_EXPIRED",reminder ? "Đã hết hạn nhận; không gửi email nhắc ngày cuối."
                        : "Không gửi được email trong 5 phút kể từ khi đơn chuyển sang Chờ nhận.",started,false,actorId);return;
        }
        if(!validEmail(event.recipientEmail())) {
            finish(event,"SKIPPED","INVALID_EMAIL","Bạn đọc chưa có email hợp lệ. Đơn vẫn ở trạng thái Chờ nhận.",started,false,actorId);return;
        }
        if(location.isBlank()) {
            finish(event,"FAILED","MISSING_PICKUP_LOCATION","Chưa khai báo địa điểm quầy nhận sách (PICKUP_LOCATION).",started,false,actorId);return;
        }
        // Reserve SMTP time; never start an attempt close to the five-minute boundary.
        long remaining=Duration.between(now(),event.sendBefore()).toMillis();
        if(remaining<1000) {
            finish(event,"FAILED","SEND_WINDOW_EXPIRED",reminder ? "Không còn đủ thời gian gửi trước hạn nhận sách."
                        : "Không còn đủ thời gian gửi trong giới hạn 5 phút.",started,false,actorId);return;
        }
        if(simulateFailure) {
            retry(event,"SIMULATED_FAILURE","Mô phỏng lỗi gửi tạm thời; chưa gửi thư thật.",started,true,actorId);return;
        }
        try {
            emails.sendRawHtmlEmailStrict(event.recipientEmail().trim(),subject,html,remaining);
            // SMTP acceptance is success; inbox delivery/read receipt is outside this contract.
            OffsetDateTime sent=now();
            if(sent.isAfter(event.sendBefore()) || (reminder && !sent.isBefore(event.sendBefore()))) {
                finish(event,"FAILED","SMTP_ACCEPTED_LATE",reminder ? "SMTP đã chấp nhận thư sau hạn nhận; không gửi lại để tránh trùng."
                        : "SMTP đã chấp nhận thư sau giới hạn 5 phút; không gửi lại để tránh trùng.",started,false,actorId);
            } else finish(event,"SENT",null,null,started,false,actorId);
        } catch(MailAuthenticationException e) {
            finish(event,"FAILED","SMTP_AUTH_FAILED","SMTP từ chối xác thực. Kiểm tra cấu hình email.",started,false,actorId);
        } catch(MailPreparationException e) {
            finish(event,"FAILED","SMTP_NOT_CONFIGURED","Chưa cấu hình đầy đủ SMTP; email chưa được gửi.",started,false,actorId);
        } catch(Exception e) {
            // A connection failure has no accepted message. An uncertain failure after DATA must not resend.
            if (e instanceof org.springframework.mail.MailSendException send && send.getFailedMessages().isEmpty()) {
                retry(event,"SMTP_TEMPORARY_FAILURE",reminder ? "Kết nối SMTP gặp lỗi tạm thời; sẽ thử lại trước hạn nhận sách."
                                : "Kết nối SMTP gặp lỗi tạm thời. Hệ thống sẽ tự thử lại trong 5 phút.",started,false,actorId);
            } else {
                Integer reply=smtpReply(e);
                if(reply!=null && reply>=400 && reply<500) {
                    retry(event,"SMTP_TEMPORARY_FAILURE",reminder ? "SMTP tạm thời từ chối thư; sẽ thử lại trước hạn nhận sách."
                                    : "SMTP tạm thời từ chối thư. Hệ thống sẽ tự thử lại trong 5 phút.",started,false,actorId);
                } else finish(event,"FAILED",reply!=null && reply>=500?"SMTP_REJECTED":"DELIVERY_UNCERTAIN",
                        reply!=null && reply>=500?"SMTP từ chối thư. Vui lòng kiểm tra email và cấu hình gửi.":
                        "Không xác định được SMTP đã nhận thư hay chưa. Không tự gửi lại để tránh trùng.",started,false,actorId);
            }
        }
    }
    private static Integer smtpReply(Throwable error) {
        java.util.Set<Throwable> visited=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Throwable> pending=new java.util.ArrayDeque<>();pending.add(error);
        while(!pending.isEmpty()) {
            Throwable current=pending.remove();if(!visited.add(current))continue;
if (current instanceof jakarta.mail.SendFailedException) {
    try {
        Object value = current.getClass()
                .getMethod("getReturnCode")
                .invoke(current);

        if (value instanceof Integer code
                && code >= 400 && code < 600) {
            return code;
        }
    } catch (ReflectiveOperationException | SecurityException ignored) {
        // Không có mã SMTP: giữ cách xử lý kết quả gửi không xác định.
    }
}
            if(current.getCause()!=null)pending.add(current.getCause());
            if(current instanceof org.springframework.mail.MailSendException mail) pending.addAll(mail.getFailedMessages().values());
            if(current instanceof jakarta.mail.MessagingException mail && mail.getNextException()!=null)pending.add(mail.getNextException());
        }
        return null;    
    }
    private void retry(Event event,String code,String message,OffsetDateTime started,boolean simulated,Long actorId) {
        OffsetDateTime finished=now();
        if ("LAST_DAY_REMINDER".equals(event.notificationType()) && event.attemptCount()+1 >= Math.max(1,reminderMaxAttempts)) {
            repository.complete(event,"FAILED","SMTP_RETRY_EXHAUSTED","Đã hết số lượt thử gửi email nhắc; không tạo email trùng.",
                    started,finished,finished,simulated,actorId);return;
        }
        // Backoff 5,10,20,30 seconds; always bound by the original event's window.
        long seconds=Math.min(30,5L << Math.min(3,event.attemptCount()));
        OffsetDateTime next=finished.plusSeconds(seconds);
        if(!next.plusSeconds(1).isBefore(event.sendBefore())) {
            repository.complete(event,"FAILED","SEND_WINDOW_EXPIRED",
                    "LAST_DAY_REMINDER".equals(event.notificationType()) ? "Gửi thất bại; không còn đủ thời gian trước hạn nhận sách."
                            : "Gửi thất bại; đã hết thời gian thử lại trong giới hạn 5 phút.",started,finished,finished,simulated,actorId);
        } else repository.complete(event,"RETRY",code,message,started,finished,next,simulated,actorId);
    }
    private void finish(Event event,String status,String code,String message,OffsetDateTime started,boolean simulated,Long actorId) {
        OffsetDateTime finished=now();repository.complete(event,status,code,message,started,finished,finished,simulated,actorId);
    }
    public static boolean validEmail(String value) {
        if(value==null || value.isBlank() || value.length()>254 || value.indexOf('\r')>=0 || value.indexOf('\n')>=0)return false;
        try {
            InternetAddress address=new InternetAddress(value.trim(),true);address.validate();
            String actual=address.getAddress();int at=actual.lastIndexOf('@');
            return actual.equals(value.trim()) && at>0 && actual.substring(at+1).contains(".");
        } catch(Exception e) { return false; }
    }
    public static String escape(String s) {
        return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
    public static String content(Event event,String location) {
        String deadline=event.pickupDeadline()==null?"Chưa khai báo":DATE.format(event.pickupDeadline().atZoneSameInstant(ZONE));
        return """
                <!doctype html><html lang="vi"><head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;color:#1e293b;line-height:1.6">
                <h2>Thư viện LIBRA — Sách đã sẵn sàng</h2><p>Kính gửi %s,</p>
                <p>Đơn đặt giữ <strong>#%d</strong> đã chuyển sang <strong>Chờ nhận</strong>.</p>
                <p>Tên sách: <strong>%s</strong><br>Hạn cuối nhận: <strong>%s (giờ Việt Nam, UTC+07:00)</strong>
                <br>Địa điểm nhận: <strong>%s</strong></p>
                <p>Vui lòng mang thẻ thư viện đến quầy nhận trước hạn. Bạn có thể xem thông tin trong mục
                “Đơn đặt giữ của tôi” trên trang cá nhân.</p><p>Email được gửi tự động từ Thư viện LIBRA.</p>
                </body></html>
                """.formatted(escape(event.readerName()),event.reservationId(),escape(event.bookTitle()),
                escape(deadline),escape(location==null || location.isBlank()?"Chưa khai báo":location));
    }

    private LocalTime sendTime() {
        return parseSendTime(lastDaySendTime);
    }
    private static LocalTime parseSendTime(String value) {
        if (value==null || !value.matches("(?:0[0-9]|1[01]):[0-5][0-9]")) {
            throw new IllegalStateException("PICKUP_LAST_DAY_SEND_TIME phải có dạng HH:mm, từ 00:00 đến 11:59.");
        }
        return LocalTime.parse(value);
    }
    public String lastDayCron() {
        return lastDayCron(lastDaySendTime);
    }
    public static String lastDayCron(String value) {
        LocalTime time=parseSendTime(value);
        return "0 "+time.getMinute()+" "+time.getHour()+" * * *";
    }
    @Scheduled(cron="#{@pickupLastDayCron}",zone="Asia/Ho_Chi_Minh",scheduler="pickupLastDayScheduler")
    public void scheduleLastDayReminders() {
        if (!automaticEnabled) return;
        OffsetDateTime point=now();
        try {
            int count=enqueueLastDay(point.toLocalDate(),point);
            log.info("Lịch nhắc ngày cuối {}: tạo {} email.",point.toLocalDate(),count);
            dispatch();
        } catch (Exception e) { log.error("Không chạy được lịch nhắc ngày cuối nhận sách.",e); }
    }
    private List<ReminderCandidate> candidates(LocalDate date) {
        return repository.reminderCandidates(date.atStartOfDay(ZONE).toOffsetDateTime(),
                date.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
    }
    private String reason(ReminderCandidate candidate,OffsetDateTime point) {
        if (!"READY_FOR_PICKUP".equals(candidate.status()) || candidate.converted()) return "Đơn đã nhận, đã hủy hoặc không còn Chờ nhận.";
        if (candidate.pickupDeadline()==null || !point.isBefore(candidate.pickupDeadline())) return "Đơn đã hết hạn nhận tại thời điểm kiểm tra.";
        if (candidate.notificationStatus()!=null) return "Đã có email nhắc cho đúng đơn và hạn nhận này; không tạo trùng.";
        if (!validEmail(candidate.recipientEmail())) return "Bạn đọc chưa có email hợp lệ.";
        if (pickupLocation.isBlank()) return "Chưa khai báo quầy nhận sách (PICKUP_LOCATION).";
        return "Đủ điều kiện nhắc ngày cuối nhận sách.";
    }
    private boolean eligible(ReminderCandidate candidate,OffsetDateTime point) {
        return "READY_FOR_PICKUP".equals(candidate.status()) && !candidate.converted()
                && candidate.pickupDeadline()!=null && point.isBefore(candidate.pickupDeadline())
                && candidate.notificationStatus()==null && validEmail(candidate.recipientEmail()) && !pickupLocation.isBlank();
    }
    private int enqueueLastDay(LocalDate date,OffsetDateTime point) {
        int count=0;
        for (ReminderCandidate candidate:candidates(date)) {
            // Invalid address/location is also recorded by the shared worker in email history.
            if (!"READY_FOR_PICKUP".equals(candidate.status()) || candidate.converted()
                    || !point.isBefore(candidate.pickupDeadline()) || candidate.notificationStatus()!=null) continue;
            boolean created=Boolean.TRUE.equals(transactions.execute(tx -> repository.enqueueReminder(candidate,point,pickupLocation())));
            if (created) count++;
        }
        return count;
    }
    public Report previewLastDay(LocalDate date) {
        validateReminderDate(date);
        OffsetDateTime point=date.atTime(sendTime()).atZone(ZONE).toOffsetDateTime();
        return lastDayReport(date,point,true,0);
    }
    public Report runLastDayToday(LocalDate date,Long actorId) {
        validateReminderDate(date);
        OffsetDateTime point=now();
        if (!date.equals(point.toLocalDate())) throw new ApiException(HttpStatus.BAD_REQUEST,"REMINDER_DATE_NOT_TODAY",
                "Chỉ gửi email thật cho ngày hôm nay theo giờ Việt Nam. Ngày khác chỉ được xem trước.");
        if (point.toLocalTime().isBefore(sendTime())) throw new ApiException(HttpStatus.CONFLICT,"BEFORE_REMINDER_TIME",
                "Chưa đến giờ gửi nhắc "+lastDaySendTime+". Bạn có thể xem trước danh sách.");
        int count=enqueueLastDay(date,point);
        // SMTP is asynchronous so a batch cannot exceed the frontend's 30-second request timeout.
        // Durable claims, reservation locks and result recording are shared with the automatic worker.
        dispatchWorkers(true,actorId,automaticEnabled ? 25 : Integer.MAX_VALUE);
        return lastDayReport(date,now(),false,count);
    }
    private Report lastDayReport(LocalDate date,OffsetDateTime point,boolean preview,int count) {
        List<Entry> entries=candidates(date).stream().map(candidate -> new Entry(candidate.reservationId(),
                candidate.readerName(),candidate.recipientEmail(),candidate.bookTitle(),candidate.pickupDeadline(),pickupLocation(),
                eligible(candidate,point),reason(candidate,point),candidate.notificationStatus(),lastDaySubject(candidate.reservationId()),
                lastDayContent(candidate.readerName(),candidate.reservationId(),candidate.bookTitle(),candidate.pickupDeadline(),pickupLocation()))).toList();
        String message=preview ? "Xem trước theo trạng thái hiện tại tại "+lastDaySendTime+" của ngày chọn. Không gửi email và không ghi lịch sử."
                : "Đã tạo "+count+" email nhắc mới. Xem kết quả gửi ở từng đơn; các email đang chờ sẽ được hệ thống xử lý tiếp.";
        return new Report(date,point,lastDaySendTime,ZONE.getId(),preview,count,
                (int)entries.stream().filter(Entry::eligible).count(),message,entries);
    }
    private static void validateReminderDate(LocalDate date) {
        if (date==null) throw new ApiException(HttpStatus.BAD_REQUEST,"REMINDER_DATE_REQUIRED","Vui lòng chọn ngày chạy lịch nhắc.");
    }
    public static String lastDaySubject(long id) { return "Hôm nay là ngày cuối cùng nhận sách — Đơn #"+id; }
    public static String lastDayContent(String reader,long id,String book,OffsetDateTime pickupDeadline,String location) {
        String deadline=pickupDeadline==null ? "Chưa khai báo" : DATE.format(pickupDeadline.atZoneSameInstant(ZONE));
        return """
                <!doctype html><html lang="vi"><head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;color:#1e293b;line-height:1.6">
                <h2>Thư viện LIBRA — Nhắc ngày cuối</h2><p>Kính gửi %s,</p>
                <p><strong>Hôm nay là ngày cuối cùng nhận sách.</strong> Đơn đặt giữ <strong>#%d</strong> vẫn đang Chờ nhận.</p>
                <p>Tên sách: <strong>%s</strong><br>Hạn cuối nhận: <strong>%s (giờ Việt Nam, UTC+07:00)</strong>
                <br>Địa điểm quầy nhận: <strong>%s</strong></p>
                <p>Vui lòng mang thẻ thư viện đến quầy nhận trước thời hạn trên. Xem chi tiết trong mục
                “Đơn đặt giữ của tôi” trên trang cá nhân.</p><p>Email được gửi tự động từ Thư viện LIBRA.</p>
                </body></html>
                """.formatted(escape(reader),id,escape(book),escape(deadline),escape(location==null ? "Chưa khai báo" : location));
    }

    private static void validateId(long id) {
        if(id<1)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_RESERVATION_ID","Mã đơn đặt giữ không hợp lệ.");
    }
}
