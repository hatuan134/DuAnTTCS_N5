package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class CardLockSuggestionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public CardLockSuggestionRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    // All scans/decisions/config changes serialize before taking the existing per-reader lock.
    public void lockScan() { jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(40430001)", Integer.class); }
    public Settings settings() {
        return jdbc.queryForObject("SELECT * FROM card_lock_suggestion_settings WHERE id=1", (rs,n) ->
            new Settings(rs.getLong("debt_threshold_vnd"), rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("updated_by", Long.class), rs.getObject("last_checked_at", OffsetDateTime.class)));
    }
    public void updateThreshold(long amount, Long actor, OffsetDateTime now) {
        jdbc.update("UPDATE card_lock_suggestion_settings SET debt_threshold_vnd=?, updated_by=?, updated_at=? WHERE id=1", amount,actor,now);
    }
    public void checkedAt(OffsetDateTime now) { jdbc.update("UPDATE card_lock_suggestion_settings SET last_checked_at=? WHERE id=1",now); }
    public List<Long> cardIds() { return jdbc.query("SELECT id FROM library_cards ORDER BY id", (rs,n)->rs.getLong(1)); }
    public Long cardId(Long id) {
        return jdbc.query("SELECT card_id FROM card_lock_suggestions WHERE id=?", (rs,n)->rs.getLong(1),id).stream().findFirst().orElse(null);
    }
    private static final String SELECT = """
        SELECT s.*, CASE WHEN c.status='DISABLED' OR u.status='DISABLED' THEN 'DISABLED'
            WHEN c.status='LOCKED' OR u.status='LOCKED' OR u.locked_until > clock_timestamp()
                OR (c.manual_lock_released_at IS NULL AND c.manual_lock_started_at <= clock_timestamp()
                    AND (c.manual_lock_ends_at IS NULL OR c.manual_lock_ends_at > clock_timestamp())) THEN 'LOCKED'
            WHEN c.status='EXPIRED' OR c.expires_at < (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'EXPIRED'
            ELSE c.status END AS current_card_status
        FROM card_lock_suggestions s JOIN library_cards c ON c.id=s.card_id JOIN users u ON u.id=c.user_id
        """;
    private static final RowMapper<Suggestion> ROW = (rs,n) -> new Suggestion(rs.getLong("id"),rs.getLong("card_id"),
        rs.getString("card_number"),rs.getString("reader_name"),rs.getLong("max_overdue_days"),rs.getBigDecimal("unpaid_fees_vnd"),
        rs.getLong("debt_threshold_vnd"),rs.getString("reason_code"),rs.getString("status"),rs.getBoolean("episode_open"),
        rs.getObject("detected_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class),rs.getObject("decided_by",Long.class),
        rs.getString("decider_name"),rs.getObject("decided_at",OffsetDateTime.class),rs.getString("decision_reason"),
        rs.getString("card_status_after_decision"),rs.getObject("lock_ends_at",OffsetDateTime.class),
        rs.getObject("resolved_at",OffsetDateTime.class),rs.getString("current_card_status"));
    public Optional<Suggestion> find(Long id) { return jdbc.query(SELECT+" WHERE s.id=?", ROW,id).stream().findFirst(); }
    public Optional<Suggestion> openEpisode(Long cardId) { return jdbc.query(SELECT+" WHERE s.card_id=? AND s.episode_open",ROW,cardId).stream().findFirst(); }
    public long count(boolean history) { return jdbc.queryForObject("SELECT COUNT(*) FROM card_lock_suggestions WHERE "+(history?"status<>'PENDING'":"status='PENDING'"),Long.class); }
    public List<Suggestion> list(boolean history,int size,long offset) {
        return jdbc.query(SELECT+" WHERE "+(history?"s.status<>'PENDING'":"s.status='PENDING'")+" ORDER BY s.detected_at DESC,s.id DESC LIMIT ? OFFSET ?",ROW,size,offset);
    }
    public Long create(Long cardId,String number,String name,long days,BigDecimal debt,long threshold,String reason,Evidence evidence,OffsetDateTime now) {
        return jdbc.queryForObject("""
            INSERT INTO card_lock_suggestions(card_id,card_number,reader_name,max_overdue_days,unpaid_fees_vnd,
                debt_threshold_vnd,reason_code,evidence,detected_at,updated_at)
            VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,?) RETURNING id
            """,Long.class,cardId,number,name,days,debt,threshold,reason,mapper.writeValueAsString(evidence),now,now);
    }
    public void updatePending(Long id,long days,BigDecimal debt,long threshold,String reason,Evidence evidence,OffsetDateTime now) {
        jdbc.update("""
            UPDATE card_lock_suggestions SET max_overdue_days=?,unpaid_fees_vnd=?,debt_threshold_vnd=?,reason_code=?,
                evidence=CAST(? AS jsonb),updated_at=? WHERE id=? AND status='PENDING'
            """,days,debt,threshold,reason,mapper.writeValueAsString(evidence),now,id);
    }
    public void endEpisode(Long id,OffsetDateTime now) {
        jdbc.update("""
            UPDATE card_lock_suggestions SET status=CASE WHEN status='PENDING' THEN 'RESOLVED' ELSE status END,
                episode_open=FALSE,resolved_at=?,updated_at=? WHERE id=?
            """,now,now,id);
    }
    public void closeLockCycle(Long id,OffsetDateTime now) {
        jdbc.update("UPDATE card_lock_suggestions SET episode_open=FALSE,updated_at=? WHERE id=?",now,id);
    }
    public void cancelLocked(Long id,OffsetDateTime now) {
        jdbc.update("UPDATE card_lock_suggestions SET status='CANCELLED_LOCKED',updated_at=? WHERE id=? AND status='PENDING'",now,id);
    }
    public void decide(Long id,String status,Long actor,String actorName,String reason,String cardStatus,OffsetDateTime end,OffsetDateTime now) {
        if(jdbc.update("""
            UPDATE card_lock_suggestions SET status=?,decided_by=?,decider_name=?,decided_at=?,decision_reason=?,
                card_status_after_decision=?,lock_ends_at=?,updated_at=? WHERE id=? AND status='PENDING'
            """,status,actor,actorName,now,reason,cardStatus,end,now,id)!=1) throw new IllegalStateException("Đề xuất đã được xử lý.");
    }
    public Evidence evidence(Long id) {
        return mapper.readValue(jdbc.queryForObject("SELECT evidence::text FROM card_lock_suggestions WHERE id=?",String.class,id),Evidence.class);
    }
    public List<OverdueItem> overdue(Long reader,OffsetDateTime todayStart) {
        return jdbc.query("""
            SELECT l.id,l.loan_number,li.id AS item_id,b.title,c.barcode,li.due_date FROM loan_items li
            JOIN loans l ON l.id=li.loan_id JOIN book_copies c ON c.id=li.book_copy_id JOIN books b ON b.id=c.book_id
            WHERE l.borrower_user_id=? AND li.returned_at IS NULL AND li.due_date < ? ORDER BY li.due_date,li.id
            """,(rs,n)->new OverdueItem(rs.getLong("id"),rs.getString("loan_number"),rs.getLong("item_id"),
            rs.getString("title"),rs.getString("barcode"),rs.getObject("due_date",OffsetDateTime.class),0),reader,todayStart);
    }
    public List<OutstandingFee> fees(Long reader) {
        return jdbc.query("""
            SELECT id,description,amount_vnd,paid_amount_vnd,waived_amount_vnd,
                amount_vnd-paid_amount_vnd-waived_amount_vnd AS outstanding FROM reader_fees f
            WHERE reader_user_id=? AND paid_amount_vnd+waived_amount_vnd < amount_vnd
                AND NOT EXISTS (SELECT 1 FROM book_incidents i JOIN book_incident_adjustments a ON a.incident_id=i.id
                    WHERE i.compensation_fee_id=f.id AND a.status='CONFIRMED') ORDER BY id
            """,(rs,n)->new OutstandingFee(rs.getLong("id"),rs.getString("description"),rs.getBigDecimal("amount_vnd"),
                rs.getBigDecimal("paid_amount_vnd"),rs.getBigDecimal("waived_amount_vnd"),rs.getBigDecimal("outstanding")),reader);
    }
}
