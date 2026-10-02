package com.duanttcsn5.library.repository;
import com.duanttcsn5.library.dto.bookcopy.BookCopyStatusHistoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
@Repository
public class BookCopyLifecycleRepository {
    private final JdbcTemplate jdbc;
    public BookCopyLifecycleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean hasUnreturnedLoan(Long copyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loan_items WHERE book_copy_id = ? AND returned_at IS NULL)",
                Boolean.class, copyId));
    }
    public void append(Long copyId, String before, Long actorId, String reason) {
        int inserted = jdbc.update("""
                INSERT INTO book_copy_status_history
                    (book_copy_id, previous_status, new_status, actor_user_id, actor_name, reason)
                SELECT ?, ?, 'REPAIR', id, full_name, ? FROM users WHERE id = ?
                """, copyId, before, reason, actorId);
        if (inserted != 1) throw new IllegalStateException("Không thể ghi nhận người thực hiện.");
    }
    public List<BookCopyStatusHistoryResponse> history(Long copyId) {
        return jdbc.query("""
                SELECT * FROM book_copy_status_history WHERE book_copy_id = ? ORDER BY changed_at DESC, id DESC
                """, (rs, n) -> new BookCopyStatusHistoryResponse(rs.getLong("id"),
                rs.getString("previous_status"), rs.getString("new_status"), rs.getLong("actor_user_id"),
                rs.getString("actor_name"), rs.getObject("changed_at", OffsetDateTime.class), rs.getString("reason")), copyId);
    }
}
