package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.incident.IncidentDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BookIncidentRepository {
    private final JdbcTemplate jdbc;
    public BookIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Factors factors(boolean lock) {
        return jdbc.queryForObject("SELECT * FROM library_compensation_settings WHERE id = 1" + (lock ? " FOR SHARE" : ""),
                (rs, n) -> new Factors(rs.getBigDecimal("lost_factor"), rs.getBigDecimal("damaged_factor"),
                        rs.getObject("updated_at", OffsetDateTime.class)));
    }
    public void updateFactors(Factors factors, Long actor) {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=?, damaged_factor=?, updated_by=?, updated_at=clock_timestamp() WHERE id=1",
                factors.lost(), factors.damaged(), actor);
    }
    private static final String ITEM_SELECT = """
            SELECT li.id AS item_id, l.id AS loan_id, l.loan_number, l.borrower_user_id, u.full_name,
                   c.id AS copy_id, c.barcode, b.title, li.due_date, c.status, li.returned_at,
                   li.resolution_reason, c.cover_price
            FROM loan_items li JOIN loans l ON l.id=li.loan_id JOIN users u ON u.id=l.borrower_user_id
            JOIN book_copies c ON c.id=li.book_copy_id JOIN books b ON b.id=c.book_id
            """;
    private static final RowMapper<Item> ITEM_MAPPER = (rs, n) -> new Item(rs.getLong("item_id"), rs.getLong("loan_id"),
            rs.getString("loan_number"), rs.getLong("borrower_user_id"), rs.getString("full_name"),
            rs.getLong("copy_id"), rs.getString("barcode"), rs.getString("title"),
            rs.getObject("due_date", OffsetDateTime.class), rs.getString("status"),
            rs.getObject("returned_at", OffsetDateTime.class), rs.getString("resolution_reason"), rs.getBigDecimal("cover_price"));
    public List<Item> search(String query) {
        return jdbc.query(ITEM_SELECT + """
                WHERE li.returned_at IS NULL AND (strpos(lower(l.loan_number),lower(?))>0
                  OR strpos(lower(c.barcode),lower(?))>0 OR strpos(lower(u.full_name),lower(?))>0
                  OR EXISTS (SELECT 1 FROM library_cards card WHERE card.user_id=u.id
                      AND strpos(lower(card.card_number),lower(?))>0))
                ORDER BY l.borrowed_at DESC,l.id DESC,li.id LIMIT 100
                """, ITEM_MAPPER, query,query,query,query);
    }
    public Optional<Item> item(Long id) {
        return jdbc.query(ITEM_SELECT + " WHERE li.id=?", ITEM_MAPPER, id).stream().findFirst();
    }
    public void lockReader(Long readerId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE", Long.class, readerId);
    }
    public Long compensation(Item item, String type, long amount) {
        // Zero rounded VND still has a receivable/audit record; payment lists omit zero debt.
        return jdbc.queryForObject("""
                INSERT INTO reader_fees(reader_user_id,description,amount_vnd,compensation_item_id)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class,item.readerId(),"Bồi thường " + ("LOST".equals(type)?"Mất":"Hỏng nặng")
                + " · " + item.loanNumber() + " · " + item.barcode(),amount,item.itemId());
    }
    public Long insert(Item item,String type,String description,BigDecimal factor,long amount,Long feeId,
                       Long actor,String actorName,OffsetDateTime at,long automatic,String adjustmentReason) {
        return jdbc.queryForObject("""
                INSERT INTO book_incidents(item_id,copy_id,reader_user_id,incident_type,description,cover_price,
                  factor,compensation_vnd,compensation_fee_id,actor_user_id,actor_name,recorded_at,before_status,after_status,automatic_compensation_vnd,adjustment_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'BORROWED','REMOVED',?,?) RETURNING id
                """,Long.class,item.itemId(),item.copyId(),item.readerId(),type,description,item.coverPrice(),
                factor,amount,feeId,actor,actorName,at,automatic,adjustmentReason);
    }
    public void adjustmentAudit(Long id,Item item,BigDecimal factor,long before,long after,String reason,Long actor,String actorName) {
        // PostgreSQL constructs JSON safely; the audit participates in the incident transaction.
        jdbc.update("""
                INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,after_data,created_at)
                VALUES (?,'COMPENSATION_ADJUSTED','BOOK_INCIDENT',?,
                  jsonb_build_object('itemId',CAST(? AS bigint),'coverPrice',CAST(? AS numeric),
                    'factor',CAST(? AS numeric),'beforeAmountVnd',CAST(? AS bigint),'afterAmountVnd',CAST(? AS bigint),
                    'reason',CAST(? AS text),'actorUserId',CAST(? AS bigint),'actorName',CAST(? AS text)),CURRENT_TIMESTAMP)
                """,actor,id.toString(),item.itemId(),item.coverPrice(),factor,before,after,reason,actor,actorName);
    }
    public int close(Item item,String type,OffsetDateTime at,Long actor,String actorName) {
        return jdbc.update("""
                UPDATE loan_items SET returned_at=?,returned_by=?,returned_by_name=?,resolution_reason=?
                WHERE id=? AND returned_at IS NULL
                """,at,actor,actorName,type,item.itemId());
    }
    public void history(Item item,String type,String description,Long actor,String actorName,OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,changed_at,reason)
                VALUES (?,'BORROWED','REMOVED',?,?,?,?)
                """,item.copyId(),actor,actorName,at,("LOST".equals(type)?"Mất: ":"Hỏng nặng: ")+description);
    }
    public void photo(Long id,String filename,String contentType,byte[] bytes) {
        jdbc.update("INSERT INTO book_incident_images(incident_id,filename,content_type,image_data) VALUES (?,?,?,?)",
                id,filename,contentType,bytes);
    }
    public List<Photo> photos(Long id) {
        return jdbc.query("SELECT id,filename,content_type FROM book_incident_images WHERE incident_id=? ORDER BY id",
                (rs,n)->new Photo(rs.getLong("id"),rs.getString("filename"),rs.getString("content_type")),id);
    }
    public Optional<Image> image(Long incidentId,Long imageId) {
        return jdbc.query("SELECT content_type,image_data FROM book_incident_images WHERE incident_id=? AND id=?",
                (rs,n)->new Image(rs.getString("content_type"),rs.getBytes("image_data")),incidentId,imageId).stream().findFirst();
    }
    public record Row(Long id,Long itemId,String type,String description,BigDecimal price,BigDecimal factor,
                      long amount,Long feeId,String actor,OffsetDateTime at,String before,String after,long automatic,String reason,Long actorId) {}
    public Optional<Row> row(Long id) {
        return jdbc.query("SELECT * FROM book_incidents WHERE id=?",(rs,n)->new Row(rs.getLong("id"),rs.getLong("item_id"),
                rs.getString("incident_type"),rs.getString("description"),rs.getBigDecimal("cover_price"),rs.getBigDecimal("factor"),
                rs.getLong("compensation_vnd"),rs.getLong("compensation_fee_id"),rs.getString("actor_name"),
                rs.getObject("recorded_at",OffsetDateTime.class),rs.getString("before_status"),rs.getString("after_status"),rs.getLong("automatic_compensation_vnd"),rs.getString("adjustment_reason"),rs.getLong("actor_user_id")),id).stream().findFirst();
    }
    public List<Long> incidentIds(String query) {
        return jdbc.query("""
                SELECT i.id FROM book_incidents i JOIN loan_items li ON li.id=i.item_id
                JOIN loans l ON l.id=li.loan_id JOIN book_copies c ON c.id=i.copy_id JOIN users u ON u.id=i.reader_user_id
                WHERE strpos(lower(l.loan_number),lower(?))>0 OR strpos(lower(c.barcode),lower(?))>0
                  OR strpos(lower(u.full_name),lower(?))>0 ORDER BY i.recorded_at DESC,i.id DESC LIMIT 100
                """,(rs,n)->rs.getLong("id"),query,query,query);
    }
    public List<Adjustment> adjustments(Long id) {
        return jdbc.query("SELECT * FROM book_incident_adjustments WHERE incident_id=? ORDER BY id",(rs,n)->
            new Adjustment(rs.getLong("id"),rs.getString("adjustment_number"),rs.getLong("incident_id"),
            rs.getString("reason"),rs.getString("case_type"),rs.getString("target_status"),rs.getString("status"),
            rs.getString("requester_name"),rs.getObject("requested_at",OffsetDateTime.class),rs.getString("confirmer_name"),
            rs.getObject("confirmed_at",OffsetDateTime.class),rs.getObject("reversed_vnd",Long.class),
            rs.getObject("cancelled_debt_vnd",Long.class),rs.getObject("refund_due_vnd",Long.class),
            rs.getString("before_data"),rs.getString("after_data")),id);
    }
    public Long bookId(Long copyId) { return jdbc.queryForObject("SELECT book_id FROM book_copies WHERE id=?",Long.class,copyId); }
    public OffsetDateTime now() { return jdbc.queryForObject("SELECT clock_timestamp()",OffsetDateTime.class); }
    public void requestedAdjustmentAudit(Long id,Long actor) {
        jdbc.update("""
            INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,after_data,created_at)
            SELECT ?,'INCIDENT_ADJUSTMENT_REQUESTED','BOOK_INCIDENT_ADJUSTMENT',id::text,to_jsonb(a),clock_timestamp()
            FROM book_incident_adjustments a WHERE id=?
            """,actor,id);
    }
    public void lockIncident(Long id) { jdbc.queryForObject("SELECT id FROM book_incidents WHERE id=? FOR UPDATE",Long.class,id); }
    public long paid(Long feeId,boolean lock) {
        return jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?"+(lock?" FOR UPDATE":""),Long.class,feeId);
    }
    public Long requestAdjustment(Long incidentId,AdjustmentRequest request,Long actor,String name) {
        return jdbc.queryForObject("""
            INSERT INTO book_incident_adjustments(incident_id,reason,case_type,target_status,requested_by,requester_name)
            VALUES (?,?,?,?,?,?) RETURNING id
            """,Long.class,incidentId,request.reason(),request.caseType(),request.targetStatus(),actor,name);
    }
    public String snapshot(Item item) {
        return jdbc.queryForObject("""
            SELECT jsonb_build_object('item',to_jsonb(li),'copy',to_jsonb(c),'fee',to_jsonb(f),
              'loanStatus',CAST(? AS text))::text FROM loan_items li JOIN book_copies c ON c.id=li.book_copy_id
              JOIN reader_fees f ON f.compensation_item_id=li.id WHERE li.id=?
            """,String.class,loanStatus(item.loanId()),item.itemId());
    }
    public OffsetDateTime confirmAdjustment(Long id,Long actor,String name,String before,String after,OffsetDateTime at) {
        return jdbc.queryForObject("""
            UPDATE book_incident_adjustments SET status='CONFIRMED',confirmed_by=?,confirmer_name=?,
              before_data=CAST(? AS jsonb),after_data=CAST(? AS jsonb),confirmed_at=? WHERE id=? AND status='PENDING' RETURNING confirmed_at
            """,OffsetDateTime.class,actor,name,before,after,at,id);
    }
    public String projectedSnapshot(Item item,String target,String copyStatus,OffsetDateTime at,Long actor,String name) {
        boolean reopen="BORROWED".equals(target);
        return jdbc.queryForObject("""
            SELECT jsonb_build_object('item',to_jsonb(li)||jsonb_build_object('returned_at',CAST(? AS timestamptz),
              'returned_by',CAST(? AS bigint),'returned_by_name',CAST(? AS text),'resolution_reason',CAST(? AS text)),
              'copy',to_jsonb(c)||jsonb_build_object('status',CAST(? AS text)), 'fee',to_jsonb(f),
              'loanStatus',CAST(? AS text))::text
            FROM loan_items li JOIN book_copies c ON c.id=li.book_copy_id
              JOIN reader_fees f ON f.compensation_item_id=li.id WHERE li.id=?
            """,String.class,reopen?null:at,reopen?null:actor,reopen?null:name,reopen?null:"RETURNED",copyStatus,
            projectedLoanStatus(item,target),item.itemId());
    }
    public void applyAdjustment(Item item,Adjustment a,OffsetDateTime at,Long actor,String name,String copyStatus) {
        boolean reopen="BORROWED".equals(a.targetStatus());
        jdbc.update("UPDATE loan_items SET returned_at=?,returned_by=?,returned_by_name=?,resolution_reason=? WHERE id=?",
            reopen?null:at,reopen?null:actor,reopen?null:name,reopen?null:"RETURNED",item.itemId());
        jdbc.update("UPDATE book_copies SET status=? WHERE id=?",copyStatus,item.copyId());
        jdbc.update("""
            INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,changed_at,reason)
            VALUES (?,'REMOVED',?,?,?,?,?)
            """,item.copyId(),copyStatus,actor,name,at,a.number()+": "+a.reason());
    }
    public void adjustmentAudit(Adjustment a,Long actor,String before,String after) {
        jdbc.update("""
            INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,before_data,after_data,created_at)
            VALUES (?,'INCIDENT_REVERSED','BOOK_INCIDENT_ADJUSTMENT',?,CAST(? AS jsonb),
              jsonb_build_object('adjustment',CAST(? AS jsonb),'values',CAST(? AS jsonb)),clock_timestamp())
            """,actor,a.id().toString(),before,jdbc.queryForObject("SELECT to_jsonb(a)::text FROM book_incident_adjustments a WHERE id=?",String.class,a.id()),after);
    }
    public String projectedLoanStatus(Item item,String target) {
        if ("BORROWED".equals(target)) return "BORROWED";
        return jdbc.queryForObject("""
            SELECT CASE WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND returned_at IS NULL) THEN 'BORROWED'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='LOST')
             AND EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='DAMAGED') THEN 'MIXED_INCIDENT'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='LOST') THEN 'LOST'
            WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND id<>? AND resolution_reason='DAMAGED') THEN 'DAMAGED'
            ELSE 'RETURNED' END
            """,String.class,item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId(),item.loanId(),item.itemId());
    }
    public String loanStatus(Long loanId) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND returned_at IS NULL) THEN 'BORROWED'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='LOST')
                    AND EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='DAMAGED') THEN 'MIXED_INCIDENT'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='LOST') THEN 'LOST'
                  WHEN EXISTS(SELECT 1 FROM loan_items WHERE loan_id=? AND resolution_reason='DAMAGED') THEN 'DAMAGED'
                  ELSE 'RETURNED' END
                """,String.class,loanId,loanId,loanId,loanId,loanId);
    }
}
