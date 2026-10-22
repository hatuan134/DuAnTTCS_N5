package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.Attempt;
import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReadyPickupNotificationRepository {
    private final JdbcTemplate jdbc;
    public ReadyPickupNotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String financialSnapshot(long id) {return jdbc.queryForObject("SELECT financial_snapshot::text FROM ready_pickup_notifications WHERE id=?",String.class,id);}
    public String depositEstimate(long reservation) {return jdbc.queryForObject("SELECT deposit_reservation_estimate(?)::text",String.class,reservation);}

    public record Event(long id, long reservationId, String recipientEmail, String readerName,
                        String bookTitle, OffsetDateTime pickupDeadline, String pickupLocation,
                        OffsetDateTime eventAt, OffsetDateTime sendBefore, String status,
                        int attemptCount, String notificationType, Long bookId,
                        String cancellationReason, OffsetDateTime cancelledAt) {
        public Event(long id, long reservationId, String recipientEmail, String readerName,
                     String bookTitle, OffsetDateTime pickupDeadline, String pickupLocation,
                     OffsetDateTime eventAt, OffsetDateTime sendBefore, String status,
                     int attemptCount, String notificationType) {
            this(id,reservationId,recipientEmail,readerName,bookTitle,pickupDeadline,pickupLocation,
                    eventAt,sendBefore,status,attemptCount,notificationType,null,null,null);
        }
        public Event(long id, long reservationId, String recipientEmail, String readerName,
                     String bookTitle, OffsetDateTime pickupDeadline, String pickupLocation,
                     OffsetDateTime eventAt, OffsetDateTime sendBefore, String status, int attemptCount) {
            this(id, reservationId, recipientEmail, readerName, bookTitle, pickupDeadline,
                    pickupLocation, eventAt, sendBefore, status, attemptCount, "READY_FOR_PICKUP");
        }
    }
    public record ReminderCandidate(long reservationId, String status, String recipientEmail,
                                    String readerName, String bookTitle, OffsetDateTime pickupDeadline,
                                    boolean converted, String notificationStatus) {}
    public record ReservationState(String status, OffsetDateTime pickupDeadline, boolean converted,
                                   String cancellationReason, OffsetDateTime cancelledAt,
                                   Long cancelledBy, String cancelledByName) {
        public ReservationState(String status, OffsetDateTime pickupDeadline, boolean converted) {
            this(status,pickupDeadline,converted,null,null,null,null);
        }
    }
    public record CancellationCandidate(long reservationId, long bookId, String bookTitle,
                                        String readerName, String recipientEmail, String status,
                                        OffsetDateTime pickupDeadline, boolean converted) {}
    public Optional<CancellationCandidate> cancellationCandidate(long id) {
        return jdbc.query("""
                SELECT r.id,r.book_id,b.title,u.full_name,u.email,r.status,r.pickup_deadline,
                    EXISTS(SELECT 1 FROM loans l WHERE l.reservation_id=r.id)
                    OR EXISTS(SELECT 1 FROM loan_items i WHERE i.source_reservation_id=r.id) AS converted
                FROM book_reservations r JOIN books b ON b.id=r.book_id JOIN users u ON u.id=r.reader_id
                WHERE r.id=?
                """, (rs,n)->new CancellationCandidate(rs.getLong("id"),rs.getLong("book_id"),
                rs.getString("title"),rs.getString("full_name"),rs.getString("email"),rs.getString("status"),
                rs.getObject("pickup_deadline",OffsetDateTime.class),rs.getBoolean("converted")),id).stream().findFirst();
    }

    // Called ONLY inside the worker transaction. A lock lasts through SMTP + result recording.
    public Optional<Event> lockNext(Long reservationId, OffsetDateTime now) {
        return lockNext(reservationId, now, reservationId == null ? null : "READY_FOR_PICKUP");
    }
    public Optional<Event> lockNextReminder(Long reservationId, OffsetDateTime now) {
        return lockNext(reservationId, now, "LAST_DAY_REMINDER");
    }
    private Optional<Event> lockNext(Long reservationId, OffsetDateTime now, String type) {
        String sql = "SELECT * FROM ready_pickup_notifications WHERE "
                + (type == null ? "" : "notification_type = '" + type + "' AND ") + "status IN ('PENDING','RETRY') "
                + (reservationId == null ? "AND next_attempt_at <= ? " : "AND reservation_id = ? AND next_attempt_at <= ? ")
                + "ORDER BY event_at,id LIMIT 1 FOR UPDATE SKIP LOCKED";
        Object[] args = reservationId == null ? new Object[]{now} : new Object[]{reservationId, now};
        return jdbc.query(sql, (rs,n) -> new Event(rs.getLong("id"),rs.getLong("reservation_id"),
                rs.getString("recipient_email"),rs.getString("reader_name"),rs.getString("book_title"),
                rs.getObject("pickup_deadline",OffsetDateTime.class),rs.getString("pickup_location"),
                rs.getObject("event_at",OffsetDateTime.class),rs.getObject("send_before",OffsetDateTime.class),
                rs.getString("status"),rs.getInt("attempt_count"),rs.getString("notification_type"),
                (Long)rs.getObject("book_id"),rs.getString("cancellation_reason"),
                rs.getObject("cancelled_at",OffsetDateTime.class)),args).stream().findFirst();
    }
    // Commit a durable claim before contacting SMTP. A crash never turns an accepted email into a resend.
    public void claim(Event event,OffsetDateTime started,boolean simulated,Long actorId) {
        jdbc.update("UPDATE ready_pickup_notifications SET status='SENDING',attempt_count=? WHERE id=?",
                event.attemptCount()+1,event.id());
        jdbc.update("""
                INSERT INTO ready_pickup_notification_attempts(notification_id,attempt_number,started_at,outcome,simulated,actor_id)
                VALUES (?,?,?,'STARTED',?,?)
                """,event.id(),event.attemptCount()+1,started,simulated,actorId);
    }
    public boolean lockClaim(long id) {
        return !jdbc.query("SELECT id FROM ready_pickup_notifications WHERE id=? AND status='SENDING' FOR UPDATE",
                (rs,n)->rs.getLong(1),id).isEmpty();
    }
    public void failAbandoned(OffsetDateTime now) {
        // SMTP outcome after a process crash is unknown; never automatically resend it.
        jdbc.update("""
                WITH abandoned AS (
                    SELECT id FROM ready_pickup_notifications WHERE status='SENDING' AND (send_before<=?
                        OR (notification_type IN ('LAST_DAY_REMINDER','PICKUP_EXPIRED_CANCELLATION') AND EXISTS (
                            SELECT 1 FROM ready_pickup_notification_attempts a
                            WHERE a.notification_id=ready_pickup_notifications.id
                              AND a.attempt_number=ready_pickup_notifications.attempt_count
                              AND a.started_at <= ? - INTERVAL '5 minutes')))

                    FOR UPDATE SKIP LOCKED
                ), completed AS (
                    UPDATE ready_pickup_notifications n SET status='FAILED',completed_at=?,
                        error_code='DELIVERY_UNCERTAIN',error_message='Lượt gửi bị gián đoạn; không xác định được kết quả SMTP. Không tự gửi lại để tránh trùng.'
                    FROM abandoned a WHERE n.id=a.id RETURNING n.id,n.attempt_count
                )
                UPDATE ready_pickup_notification_attempts a SET outcome='FAILED',finished_at=?,
                    error_code='DELIVERY_UNCERTAIN',error_message='Lượt gửi bị gián đoạn; không tự gửi lại để tránh trùng.'
                FROM completed c WHERE a.notification_id=c.id AND a.attempt_number=c.attempt_count
                """,now,now,now,now);
    }
    public Optional<ReservationState> lockReservation(long reservationId) {
        return jdbc.query("""
                SELECT r.status,r.pickup_deadline,r.cancellation_reason,r.cancelled_at,r.cancelled_by,r.cancelled_by_name,
                    EXISTS(SELECT 1 FROM loans l WHERE l.reservation_id=r.id)
                    OR EXISTS(SELECT 1 FROM loan_items i WHERE i.source_reservation_id=r.id) AS converted
                FROM book_reservations r WHERE r.id=? FOR UPDATE OF r
                """, (rs,n) -> new ReservationState(rs.getString("status"),
                rs.getObject("pickup_deadline",OffsetDateTime.class),rs.getBoolean("converted"),
                rs.getString("cancellation_reason"),rs.getObject("cancelled_at",OffsetDateTime.class),
                (Long)rs.getObject("cancelled_by"),rs.getString("cancelled_by_name")),
                reservationId).stream().findFirst();
    }
    public boolean isLatest(Event event) {
        Long id=jdbc.queryForObject("SELECT MAX(id) FROM ready_pickup_notifications WHERE reservation_id=? AND notification_type='READY_FOR_PICKUP'",
                Long.class,event.reservationId());
        return id != null && id == event.id();
    }
    public Optional<String> reservationStatus(long id) {
        return jdbc.query("SELECT status FROM book_reservations WHERE id=?",(rs,n)->rs.getString(1),id)
                .stream().findFirst();
    }
    public void saveContent(long id,String location,String subject,String content) {
        jdbc.update("UPDATE ready_pickup_notifications SET pickup_location=?,subject=?,email_content=? WHERE id=?",
                location,subject,content,id);
    }
    public void complete(Event event,String status,String code,String message,
                         OffsetDateTime started,OffsetDateTime finished,OffsetDateTime next,
                         boolean simulated,Long actorId) {
        int number=event.attemptCount()+1;
        jdbc.update("""
                UPDATE ready_pickup_notifications SET status=?,attempt_count=?,error_code=?,error_message=?,
                    sent_at=?,completed_at=?,next_attempt_at=? WHERE id=?
                """, status,number,code,message,("SENT".equals(status) || "SMTP_ACCEPTED_LATE".equals(code))?finished:null,
                "RETRY".equals(status)?null:finished,next,event.id());
        jdbc.update("""
                INSERT INTO ready_pickup_notification_attempts(notification_id,attempt_number,
                    started_at,finished_at,outcome,error_code,error_message,simulated,actor_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(notification_id,attempt_number) DO UPDATE SET
                    finished_at=EXCLUDED.finished_at,outcome=EXCLUDED.outcome,error_code=EXCLUDED.error_code,
                    error_message=EXCLUDED.error_message,simulated=EXCLUDED.simulated,actor_id=EXCLUDED.actor_id
                """,event.id(),number,started,finished,status,code,message,simulated,actorId);
    }

    // Selected date is a Vietnam-local date, converted into exact instant boundaries by the service.
    public List<ReminderCandidate> reminderCandidates(OffsetDateTime start, OffsetDateTime end) {
        return jdbc.query("""
                SELECT r.id,r.status,u.email,u.full_name,b.title,r.pickup_deadline,
                    (r.book_copy_id IS NULL OR EXISTS(SELECT 1 FROM loans l WHERE l.reservation_id=r.id)
                     OR EXISTS(SELECT 1 FROM loan_items i WHERE i.source_reservation_id=r.id)) AS converted,
                    n.status AS notification_status
                FROM book_reservations r JOIN users u ON u.id=r.reader_id JOIN books b ON b.id=r.book_id
                LEFT JOIN ready_pickup_notifications n ON n.reservation_id=r.id
                    AND n.pickup_deadline=r.pickup_deadline AND n.notification_type='LAST_DAY_REMINDER'
                WHERE r.pickup_deadline>=? AND r.pickup_deadline<? ORDER BY r.id
                """, (rs,n)->new ReminderCandidate(rs.getLong("id"),rs.getString("status"),
                rs.getString("email"),rs.getString("full_name"),rs.getString("title"),
                rs.getObject("pickup_deadline",OffsetDateTime.class),rs.getBoolean("converted"),
                rs.getString("notification_status")), start,end);
    }
    public boolean enqueueReminder(ReminderCandidate candidate, OffsetDateTime now, String location) {
        return !jdbc.query("""
                INSERT INTO ready_pickup_notifications (reservation_id,notification_type,reader_id,
                    recipient_email,reader_name,book_title,pickup_deadline,pickup_location,
                    event_at,send_before,next_attempt_at)
                SELECT r.id,'LAST_DAY_REMINDER',u.id,u.email,u.full_name,b.title,r.pickup_deadline,?,
                    ?,r.pickup_deadline,? FROM book_reservations r
                JOIN users u ON u.id=r.reader_id JOIN books b ON b.id=r.book_id
                WHERE r.id=? AND r.status='READY_FOR_PICKUP' AND r.book_copy_id IS NOT NULL
                    AND r.pickup_deadline=? AND r.pickup_deadline>?
                    AND NOT EXISTS(SELECT 1 FROM loans l WHERE l.reservation_id=r.id)
                    AND NOT EXISTS(SELECT 1 FROM loan_items i WHERE i.source_reservation_id=r.id)
                ON CONFLICT (reservation_id,pickup_deadline) WHERE notification_type='LAST_DAY_REMINDER'
                    DO NOTHING RETURNING id
                """, (rs,n)->rs.getLong(1),location,now,now,candidate.reservationId(),
                candidate.pickupDeadline(),now).isEmpty();
    }

    public List<Notification> history(long reservationId) {
        return jdbc.query("SELECT * FROM ready_pickup_notifications WHERE reservation_id=? ORDER BY id DESC",
                (rs,n)->new Notification(rs.getLong("id"),reservationId,rs.getString("notification_type"),
                rs.getString("recipient_email"),rs.getString("reader_name"),rs.getString("book_title"),
                rs.getObject("pickup_deadline",OffsetDateTime.class),rs.getString("pickup_location"),
                rs.getObject("event_at",OffsetDateTime.class),rs.getObject("send_before",OffsetDateTime.class),
                rs.getObject("sent_at",OffsetDateTime.class),rs.getObject("completed_at",OffsetDateTime.class),
                rs.getString("status"),rs.getInt("attempt_count"),rs.getString("subject"),rs.getString("email_content"),
                rs.getString("error_code"),rs.getString("error_message"),attempts(rs.getLong("id")),
                (Long)rs.getObject("book_id"),rs.getString("cancellation_reason"),
                rs.getObject("cancelled_at",OffsetDateTime.class)),reservationId);
    }
    private List<Attempt> attempts(long id) {
        return jdbc.query("SELECT * FROM ready_pickup_notification_attempts WHERE notification_id=? ORDER BY attempt_number",
                (rs,n)->new Attempt(rs.getLong("id"),rs.getInt("attempt_number"),
                rs.getObject("started_at",OffsetDateTime.class),rs.getObject("finished_at",OffsetDateTime.class),
                rs.getString("outcome"),rs.getString("error_code"),rs.getString("error_message"),
                rs.getBoolean("simulated"),(Long)rs.getObject("actor_id")),id);
    }
}
