package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.OverdueContactResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class OverdueContactRepository {
    private final JdbcTemplate jdbc;

    public OverdueContactRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Only an existing voucher with a currently unreturned, past-due item can be contacted. */
    public boolean lockOpenOverdueLoan(long loanId, OffsetDateTime todayStart) {
        return !jdbc.query("""
                SELECT li.id FROM loan_items li
                WHERE li.loan_id = ? AND li.returned_at IS NULL
                  AND li.due_date IS NOT NULL AND li.due_date < ?
                ORDER BY li.id LIMIT 1 FOR UPDATE OF li
                """, (rs, n) -> rs.getLong("id"), loanId, todayStart).isEmpty();
    }

    public boolean loanExists(long loanId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE id = ?", Integer.class, loanId);
        return count != null && count > 0;
    }

    public OverdueContactResponse insert(long loanId, long staffId, String staffName,
                                         String note, OffsetDateTime timestamp) {
        return jdbc.queryForObject("""
                INSERT INTO overdue_loan_contacts (loan_id, staff_id, staff_name, note, contacted_at)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, loan_id, staff_id, staff_name, note, contacted_at
                """, (rs, n) -> new OverdueContactResponse(
                rs.getLong("id"), rs.getLong("loan_id"), rs.getLong("staff_id"),
                rs.getString("staff_name"), rs.getString("note"),
                rs.getObject("contacted_at", OffsetDateTime.class)),
                loanId, staffId, staffName, note, timestamp);
    }

    public List<OverdueContactResponse> history(long loanId) {
        return jdbc.query("""
                SELECT id, loan_id, staff_id, staff_name, note, contacted_at
                FROM overdue_loan_contacts WHERE loan_id = ?
                ORDER BY contacted_at DESC, id DESC
                """, (rs, n) -> new OverdueContactResponse(
                rs.getLong("id"), rs.getLong("loan_id"), rs.getLong("staff_id"),
                rs.getString("staff_name"), rs.getString("note"),
                rs.getObject("contacted_at", OffsetDateTime.class)), loanId);
    }
}
