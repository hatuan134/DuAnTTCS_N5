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
                       Long actor,String actorName,OffsetDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO book_incidents(item_id,copy_id,reader_user_id,incident_type,description,cover_price,
                  factor,compensation_vnd,compensation_fee_id,actor_user_id,actor_name,recorded_at,before_status,after_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'BORROWED','REMOVED') RETURNING id
                """,Long.class,item.itemId(),item.copyId(),item.readerId(),type,description,item.coverPrice(),
                factor,amount,feeId,actor,actorName,at);
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
                      long amount,Long feeId,String actor,OffsetDateTime at,String before,String after) {}
    public Optional<Row> row(Long id) {
        return jdbc.query("SELECT * FROM book_incidents WHERE id=?",(rs,n)->new Row(rs.getLong("id"),rs.getLong("item_id"),
                rs.getString("incident_type"),rs.getString("description"),rs.getBigDecimal("cover_price"),rs.getBigDecimal("factor"),
                rs.getLong("compensation_vnd"),rs.getLong("compensation_fee_id"),rs.getString("actor_name"),
                rs.getObject("recorded_at",OffsetDateTime.class),rs.getString("before_status"),rs.getString("after_status")),id).stream().findFirst();
    }
    public List<Long> incidentIds(String query) {
        return jdbc.query("""
                SELECT i.id FROM book_incidents i JOIN loan_items li ON li.id=i.item_id
                JOIN loans l ON l.id=li.loan_id JOIN book_copies c ON c.id=i.copy_id JOIN users u ON u.id=i.reader_user_id
                WHERE strpos(lower(l.loan_number),lower(?))>0 OR strpos(lower(c.barcode),lower(?))>0
                  OR strpos(lower(u.full_name),lower(?))>0 ORDER BY i.recorded_at DESC,i.id DESC LIMIT 100
                """,(rs,n)->rs.getLong("id"),query,query,query);
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
