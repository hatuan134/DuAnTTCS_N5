package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LoanReminderRepository {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public LoanReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EligibleLoanItemRow(
            Long itemId,
            Long loanId,
            String loanNumber,
            Long readerId,
            String readerName,
            String readerEmail,
            Long bookId,
            String bookTitle,
            String copyBarcode,
            OffsetDateTime dueDate
    ) {}

    /**
     * Find active, unreturned loan items due either on targetDate (DUE_TODAY)
     * or on targetDate + 2 days (DUE_IN_2_DAYS), evaluated in Vietnam time.
     */
    public List<EligibleLoanItemRow> findEligibleItemsForDate(LocalDate targetDate) {
        LocalDate in2Days = targetDate.plusDays(2);
        String sql = """
            SELECT li.id AS item_id,
                   li.loan_id,
                   l.loan_number,
                   l.borrower_user_id AS reader_id,
                   u.full_name AS reader_name,
                   u.email AS reader_email,
                   b.id AS book_id,
                   b.title AS book_title,
                   c.barcode AS copy_barcode,
                   li.due_date
            FROM loan_items li
            JOIN loans l ON l.id = li.loan_id
            JOIN users u ON u.id = l.borrower_user_id
            JOIN book_copies c ON c.id = li.book_copy_id
            JOIN books b ON b.id = c.book_id
            WHERE li.returned_at IS NULL
              AND (li.resolution_reason IS NULL OR li.resolution_reason NOT IN ('RETURNED', 'LOST', 'DAMAGED'))
              AND (c.status = 'BORROWED')
              AND (
                  (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = ?
                  OR (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = ?
              )
            ORDER BY l.borrower_user_id ASC, li.due_date ASC, li.id ASC
            """;

        return jdbc.query(sql, (rs, rowNum) -> new EligibleLoanItemRow(
                rs.getLong("item_id"),
                rs.getLong("loan_id"),
                rs.getString("loan_number"),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                rs.getString("reader_email"),
                rs.getLong("book_id"),
                rs.getString("book_title"),
                rs.getString("copy_barcode"),
                rs.getObject("due_date", OffsetDateTime.class)
        ), Date.valueOf(targetDate), Date.valueOf(in2Days));
    }

    public boolean isAlreadySentToday(LocalDate reminderDate, Long readerId, String reminderGroup) {
        String sql = """
            SELECT COUNT(*) FROM loan_reminder_logs
            WHERE reminder_date = ?
              AND reader_id = ?
              AND reminder_group = ?
              AND status = 'SENT'
              AND is_dry_run = FALSE
            """;
        Integer count = jdbc.queryForObject(sql, Integer.class, Date.valueOf(reminderDate), readerId, reminderGroup);
        return count != null && count > 0;
    }

    public Long insertLog(
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            String loanNumbers,
            String bookTitles,
            String itemsDetailJson,
            String subject,
            String emailContent,
            String status,
            String errorMessage,
            String triggeredBy,
            Long triggeredByUserId,
            String triggeredByUserName,
            boolean isDryRun
    ) {
        String sql = """
            INSERT INTO loan_reminder_logs (
                reminder_date, reader_id, reader_name, recipient_email,
                reminder_group, loan_item_count, loan_numbers, book_titles,
                items_detail, subject, email_content, status, error_message,
                triggered_by, triggered_by_user_id, triggered_by_user_name,
                is_dry_run, sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setDate(1, Date.valueOf(reminderDate));
            ps.setLong(2, readerId);
            ps.setString(3, readerName);
            ps.setString(4, recipientEmail);
            ps.setString(5, reminderGroup);
            ps.setInt(6, loanItemCount);
            ps.setString(7, loanNumbers);
            ps.setString(8, bookTitles);
            ps.setString(9, itemsDetailJson != null ? itemsDetailJson : "[]");
            ps.setString(10, subject);
            ps.setString(11, emailContent);
            ps.setString(12, status);
            ps.setString(13, errorMessage);
            ps.setString(14, triggeredBy);
            if (triggeredByUserId != null) {
                ps.setLong(15, triggeredByUserId);
            } else {
                ps.setNull(15, java.sql.Types.BIGINT);
            }
            ps.setString(16, triggeredByUserName);
            ps.setBoolean(17, isDryRun);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public List<LoanReminderLogResponse> findLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            int offset,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM loan_reminder_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND reminder_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (reminderGroup != null && !reminderGroup.isBlank()) {
            sql.append("AND reminder_group = ? ");
            params.add(reminderGroup.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND status = ? ");
            params.add(status.trim());
        }

        sql.append("ORDER BY sent_at DESC, id DESC OFFSET ? LIMIT ?");
        params.add(Math.max(0, offset));
        params.add(Math.max(1, limit));

        return jdbc.query(sql.toString(), (rs, rowNum) -> new LoanReminderLogResponse(
                rs.getLong("id"),
                rs.getDate("reminder_date").toLocalDate(),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                rs.getString("recipient_email"),
                rs.getString("reminder_group"),
                rs.getInt("loan_item_count"),
                rs.getString("loan_numbers"),
                rs.getString("book_titles"),
                rs.getString("items_detail"),
                rs.getString("subject"),
                rs.getString("email_content"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("triggered_by"),
                rs.getString("triggered_by_user_name"),
                rs.getBoolean("is_dry_run"),
                rs.getObject("sent_at", OffsetDateTime.class)
        ), params.toArray());
    }

    public long countLogs(LocalDate date, String reminderGroup, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM loan_reminder_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND reminder_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (reminderGroup != null && !reminderGroup.isBlank()) {
            sql.append("AND reminder_group = ? ");
            params.add(reminderGroup.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND status = ? ");
            params.add(status.trim());
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }
}
