package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.report.LoanReportResponse.TopBorrowedBook;
import com.duanttcsn5.library.dto.report.LoanReportResponse.TopQueuedBook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Aggregate directly in PostgreSQL; never load all loan slips into the application. */
@Repository
public class LoanReportRepository {
    private final JdbcTemplate jdbc;

    public LoanReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DayBorrowCount(LocalDate date, long borrowCount) {}
    public record Activity(long borrowed, long returned, List<DayBorrowCount> dailyBorrowed) {}
    public record OpenAndOverdue(long open, long overdue) {}

    /**
     * Count borrowed copies once for both the daily chart and the headline total.
     * The timestamp range uses the inclusive Vietnam calendar days, and the
     * PostgreSQL date cast uses the SAME zone to avoid midnight drift.
     * Returning an explicit row per day makes empty days and one-day reports reliable.
     */
    public Activity activity(LocalDate startDate, LocalDate endDate,
                             OffsetDateTime from, OffsetDateTime untilExclusive) {
        var rows = jdbc.query("""
                WITH borrowed_per_day AS (
                  SELECT (borrowed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS borrow_date,
                         COUNT(*) AS borrow_count
                  FROM loan_items
                  WHERE borrowed_at >= ? AND borrowed_at < ?
                  GROUP BY borrow_date
                ), returned_total AS (
                  SELECT COUNT(*) AS returned_count
                  FROM loan_items
                  WHERE returned_at >= ? AND returned_at < ?
                    AND (resolution_reason IS NULL OR resolution_reason = 'RETURNED')
                )
                SELECT (CAST(? AS date) + day.day_offset) AS report_date,
                       COALESCE(borrowed.borrow_count, 0) AS borrow_count,
                       returned_total.returned_count AS returned_count
                FROM generate_series(0, CAST(? AS date) - CAST(? AS date)) AS day(day_offset)
                CROSS JOIN returned_total
                LEFT JOIN borrowed_per_day borrowed ON borrowed.borrow_date = (CAST(? AS date) + day.day_offset)
                ORDER BY day.day_offset ASC
                """, (rs, row) -> new DayActivity(
                rs.getObject("report_date", LocalDate.class),
                rs.getLong("borrow_count"), rs.getLong("returned_count")),
                from, untilExclusive, from, untilExclusive, startDate, endDate, startDate, startDate);
        long borrowed = 0;
        List<DayBorrowCount> days = new ArrayList<>(rows.size());
        for (DayActivity row : rows) {
            days.add(new DayBorrowCount(row.date(), row.borrowCount()));
            borrowed += row.borrowCount();
        }
        return new Activity(borrowed, rows.isEmpty() ? 0 : rows.get(0).returnedCount(), days);
    }

    private record DayActivity(LocalDate date, long borrowCount, long returnedCount) {}

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

    /**
     * The existing operational queue counts PENDING reservation orders only.
     * READY_FOR_PICKUP already has an allocated copy and is no longer in line.
     * This is a CURRENT snapshot, NOT a reconstruction for the report end date:
     * the schema does not store full historical reservation-status transitions.
     * Ties are ordered by numeric book id so the Top 10 is deterministic.
     */
    public List<TopQueuedBook> topQueuedBooks(OffsetDateTime snapshotAt) {
        return jdbc.query("""
                WITH ranked AS (
                  SELECT reservations.book_id, COUNT(*) AS wait_count
                  FROM book_reservations reservations
                  WHERE reservations.status = 'PENDING'
                    AND reservations.reserved_at <= ?
                  GROUP BY reservations.book_id
                  ORDER BY wait_count DESC, reservations.book_id ASC
                  LIMIT 10
                )
                SELECT books.id AS book_id, books.title AS book_title,
                       ranked.wait_count,
                       (SELECT COUNT(*) FROM book_copies stock
                        WHERE stock.book_id = books.id AND stock.status <> 'REMOVED') AS stock_count
                FROM ranked
                JOIN books ON books.id = ranked.book_id
                ORDER BY ranked.wait_count DESC, books.id ASC
                """, (rs, row) -> new TopQueuedBook(
                rs.getLong("book_id"), rs.getString("book_title"),
                rs.getLong("wait_count"), rs.getLong("stock_count")), snapshotAt);
    }

}
