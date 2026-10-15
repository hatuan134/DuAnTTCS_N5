package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class OverdueDebtReportRepository {
    private final JdbcTemplate jdbc;
    public OverdueDebtReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record ReaderFacts(Long readerId, String cardNumber, String fullName, String phone, BigDecimal debt) {}
    public record Item(Long readerId, OffsetDateTime dueAt) {}

    public List<Item> overdueItems(OffsetDateTime todayStart) {
        return jdbc.query("""
            SELECT l.borrower_user_id, li.due_date FROM loan_items li JOIN loans l ON l.id=li.loan_id
            WHERE li.returned_at IS NULL AND li.due_date < ?
            """, (rs,n) -> new Item(rs.getLong("borrower_user_id"),rs.getObject("due_date",OffsetDateTime.class)),todayStart);
    }
    // Aggregate fees independently from loans: a many-to-many join would multiply debts.
    public List<ReaderFacts> readers(OffsetDateTime todayStart) {
        return jdbc.query("""
            WITH debt AS (
                SELECT f.reader_user_id, SUM(f.amount_vnd-f.paid_amount_vnd-f.waived_amount_vnd) AS debt
                FROM reader_fees f
                WHERE f.amount_vnd > f.paid_amount_vnd+f.waived_amount_vnd
                  AND NOT EXISTS (SELECT 1 FROM book_incidents i
                    JOIN book_incident_adjustments a ON a.incident_id=i.id
                    WHERE i.compensation_fee_id=f.id AND a.status='CONFIRMED')
                GROUP BY f.reader_user_id
            )
            SELECT u.id, c.card_number, u.full_name, u.phone, COALESCE(d.debt,0) AS debt
            FROM users u LEFT JOIN library_cards c ON c.user_id=u.id LEFT JOIN debt d ON d.reader_user_id=u.id
            WHERE d.debt>0 OR EXISTS (SELECT 1 FROM loans l JOIN loan_items li ON li.loan_id=l.id
                WHERE l.borrower_user_id=u.id AND li.returned_at IS NULL AND li.due_date < ?)
            ORDER BY COALESCE(c.card_number,''), u.id
            """, (rs,n) -> new ReaderFacts(rs.getLong("id"),rs.getString("card_number"),rs.getString("full_name"),
                rs.getString("phone"),rs.getBigDecimal("debt")),todayStart);
    }
    public Long start(Long actor,String name,OffsetDateTime time,String filter) {
        return jdbc.queryForObject("""
            INSERT INTO overdue_debt_exports(actor_user_id,actor_name,requested_at,default_filter)
            VALUES (?,?,?,?) RETURNING id
            """,Long.class,actor,name,time,filter);
    }
    public void finish(Long id,String status,int count,OffsetDateTime snapshotAt,OffsetDateTime completedAt) {
        int updated=jdbc.update("""
            UPDATE overdue_debt_exports SET status=?,record_count=?,snapshot_at=?,completed_at=?
            WHERE id=? AND status='PROCESSING'
            """,status,count,snapshotAt,completedAt,id);
        if(updated!=1) throw new IllegalStateException("Không cập nhật được nhật ký xuất dữ liệu.");
    }
    public long countHistory() { return jdbc.queryForObject("SELECT COUNT(*) FROM overdue_debt_exports",Long.class); }
    public List<ExportLog> history(int size,long offset) {
        return jdbc.query("""
            SELECT * FROM overdue_debt_exports ORDER BY requested_at DESC,id DESC LIMIT ? OFFSET ?
            """,(rs,n)->new ExportLog(rs.getLong("id"),rs.getString("actor_name"),
                rs.getObject("requested_at",OffsetDateTime.class),rs.getObject("snapshot_at",OffsetDateTime.class),
                rs.getObject("completed_at",OffsetDateTime.class),rs.getString("default_filter"),
                rs.getInt("record_count"),rs.getString("status")),size,offset);
    }
}
