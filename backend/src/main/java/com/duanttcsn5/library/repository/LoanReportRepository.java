package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.report.LoanReportResponse.TopBorrowedBook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** Aggregate directly in PostgreSQL; never load all loan slips into the application. */
@Repository
public class LoanReportRepository {
    private final JdbcTemplate jdbc;

    public LoanReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Activity(long borrowed, long returned) {}
    public record OpenAndOverdue(long open, long overdue) {}

    public Activity activity(OffsetDateTime from, OffsetDateTime untilExclusive) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM loan_items
                   WHERE borrowed_at >= ? AND borrowed_at < ?) AS borrowed,
                  (SELECT COUNT(*) FROM loan_items
                   WHERE returned_at >= ? AND returned_at < ?
                     AND (resolution_reason IS NULL OR resolution_reason = 'RETURNED')) AS returned
                """, (rs, row) -> new Activity(rs.getLong("borrowed"), rs.getLong("returned")),
                from, untilExclusive, from, untilExclusive);
    }

    /**
     * A loan slip is open if at least one copy was not yet returned at the end-date cutoff.
     * A loan slip is overdue if at least one such copy was due BEFORE the selected end date.
     * A due date on the end date itself becomes overdue only the following calendar day,
     * consistently with S3-09's definition of an overdue loan.
     */
    public OpenAndOverdue openAndOverdue(OffsetDateTime endDateStart, OffsetDateTime untilExclusive) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS open_count,
                       COUNT(*) FILTER (WHERE has_overdue_item) AS overdue_count
                FROM (
                  SELECT li.loan_id,
                         BOOL_OR(li.due_date IS NOT NULL AND li.due_date < ?) AS has_overdue_item
                  FROM loan_items li
                  WHERE li.borrowed_at < ?
                    AND (li.returned_at IS NULL OR li.returned_at >= ?)
                  GROUP BY li.loan_id
                ) open_slips
                """, (rs, row) -> new OpenAndOverdue(
                rs.getLong("open_count"), rs.getLong("overdue_count")),
                endDateStart, untilExclusive, untilExclusive);
    }

    public List<TopBorrowedBook> topBooks(OffsetDateTime from, OffsetDateTime untilExclusive) {
        return jdbc.query("""
                WITH ranked AS (
                  SELECT copies.book_id, COUNT(*) AS borrow_count
                  FROM loan_items items
                  JOIN book_copies copies ON copies.id = items.book_copy_id
                  WHERE items.borrowed_at >= ? AND items.borrowed_at < ?
                  GROUP BY copies.book_id
                  ORDER BY borrow_count DESC, copies.book_id ASC
                  LIMIT 10
                )
                SELECT books.id AS book_id, books.title AS book_title,
                       ranked.borrow_count,
                       (SELECT COUNT(*) FROM book_copies stock
                        WHERE stock.book_id = books.id AND stock.status <> 'REMOVED') AS stock_count
                FROM ranked
                JOIN books ON books.id = ranked.book_id
                ORDER BY ranked.borrow_count DESC, books.id ASC
                """, (rs, row) -> new TopBorrowedBook(
                rs.getLong("book_id"), rs.getString("book_title"),
                rs.getLong("borrow_count"), rs.getLong("stock_count")),
                from, untilExclusive);
    }
}
