package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderLogResponse;
import com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderRetryAttemptDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LoanReminderRepository {

    public static String deliveryGroup(String group) {
        return group.startsWith("OVERDUE_") ? "OVERDUE_DAILY" : group;
    }

    public String deliveryState(LocalDate date, Long readerId, String group) {
        var states = jdbc.queryForList("""
                SELECT CASE WHEN state = 'PROCESSING' AND updated_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes'
                    THEN 'REVIEW_REQUIRED' ELSE state END FROM loan_reminder_claims
                WHERE reminder_date = ? AND reader_id = ? AND reminder_group = ?
                """, String.class, Date.valueOf(date), readerId, deliveryGroup(group));
        return states.isEmpty() ? "READY" : states.get(0);
    }

    // Commit reservation and audit payload before SMTP. No database transaction spans SMTP.
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Long claimInitial(LocalDate date,
            com.duanttcsn5.library.dto.reminder.LoanReminderDtos.LoanReminderGroupDto group,
            String itemsJson, String source, Long userId, String userName) {
        int claimed = jdbc.update("""
                INSERT INTO loan_reminder_claims (reminder_date, reader_id, reminder_group, state)
                VALUES (?, ?, ?, 'PROCESSING') ON CONFLICT DO NOTHING
                """, Date.valueOf(date), group.readerId(), deliveryGroup(group.reminderGroup()));
        if (claimed == 0) return null;
        Long id = insertLog(date, group.readerId(), group.readerName(), group.readerCardNumber(),
                group.recipientEmail(), group.reminderGroup(), group.items().size(), group.totalEstimatedFeeVnd(),
                0, 3, group.items().stream().map(i -> i.loanNumber()).distinct().collect(java.util.stream.Collectors.joining(", ")),
                group.items().stream().map(i -> i.bookTitle()).distinct().collect(java.util.stream.Collectors.joining("; ")),
                itemsJson, group.subject(), group.emailContentHtml(), "PROCESSING", null, source, userId, userName, false);
        jdbc.update("UPDATE loan_reminder_claims SET log_id = ? WHERE reminder_date = ? AND reader_id = ? AND reminder_group = ?",
                id, Date.valueOf(date), group.readerId(), deliveryGroup(group.reminderGroup()));
        return id;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean claimRetry(Long id, int expectedRetry) {
        int claimed = jdbc.update("""
                UPDATE loan_reminder_claims c SET state = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
                FROM loan_reminder_logs l
                WHERE c.log_id = ? AND l.id = c.log_id AND c.state = 'PENDING_RETRY'
                  AND l.status = 'PENDING_RETRY' AND l.retry_count = ? AND l.retry_count < l.max_retries
                  AND l.is_dry_run = FALSE
                  AND NOT EXISTS (SELECT 1 FROM loan_reminder_logs s
                      WHERE s.reminder_date = c.reminder_date AND s.reader_id = c.reader_id
                        AND (CASE WHEN s.reminder_group LIKE 'OVERDUE_%' THEN 'OVERDUE_DAILY' ELSE s.reminder_group END) = c.reminder_group
                        AND s.status = 'SENT' AND s.is_dry_run = FALSE)
                """, id, expectedRetry);
        if (claimed == 1) jdbc.update("UPDATE loan_reminder_logs SET status = 'PROCESSING' WHERE id = ?", id);
        return claimed == 1;
    }

    public boolean retryItemsStillEligible(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT reminder_date = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                  AND jsonb_array_length(items_detail) > 0
                  AND NOT EXISTS (
                    SELECT 1 FROM jsonb_array_elements(items_detail) item
                    LEFT JOIN loan_items li ON li.id = (item->>'itemId')::bigint
                    LEFT JOIN book_copies c ON c.id = li.book_copy_id
                    WHERE li.id IS NULL OR li.returned_at IS NOT NULL
                      OR li.resolution_reason IN ('RETURNED','LOST','DAMAGED')
                      OR c.status IS DISTINCT FROM 'BORROWED'
                      OR li.due_date IS DISTINCT FROM (item->>'dueDate')::timestamptz)
                FROM loan_reminder_logs WHERE id = ?
                """, Boolean.class, id));
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void finishDelivery(Long id, String state, int retryCount, String reason, String source) {
        int changed = jdbc.update("""
                UPDATE loan_reminder_claims SET state = ?, updated_at = CURRENT_TIMESTAMP
                WHERE log_id = ? AND state = 'PROCESSING'
                """, state, id);
        if (changed != 1) throw new IllegalStateException("Reminder claim ownership lost");
        updateLogRetryStatus(id, state, retryCount, reason);
        Integer attempt = jdbc.queryForObject("SELECT COALESCE(MAX(attempt_number), 0) + 1 FROM loan_reminder_retry_attempts WHERE reminder_log_id = ?", Integer.class, id);
        insertRetryAttempt(id, attempt, "SENT".equals(state) ? "SUCCESS"
                : "REVIEW_REQUIRED".equals(state) ? "UNKNOWN" : "SKIPPED".equals(state) ? "SKIPPED" : "FAILED",
                reason, source);
    }

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
            String readerCardNumber,
            String readerEmail,
            Long bookId,
            String bookTitle,
            String copyBarcode,
            OffsetDateTime dueDate
    ) {
        public EligibleLoanItemRow(
                Long itemId, Long loanId, String loanNumber, Long readerId,
                String readerName, String readerEmail, Long bookId, String bookTitle,
                String copyBarcode, OffsetDateTime dueDate
        ) {
            this(itemId, loanId, loanNumber, readerId, readerName, null, readerEmail, bookId, bookTitle, copyBarcode, dueDate);
        }
    }

    /**
     * Find active, unreturned loan items due on:
     * - targetDate + 2 days (DUE_IN_2_DAYS)
     * - targetDate (DUE_TODAY)
     * - every past due date (OVERDUE_DAILY)
     * evaluated in Vietnam time. Excludes returned and resolved items.
     */
    public List<EligibleLoanItemRow> findEligibleItemsForDate(LocalDate targetDate) {
        LocalDate in2Days = targetDate.plusDays(2);

        String sql = """
            SELECT li.id AS item_id,
                   li.loan_id,
                   l.loan_number,
                   l.borrower_user_id AS reader_id,
                   u.full_name AS reader_name,
                   lc.card_number AS reader_card_number,
                   u.email AS reader_email,
                   b.id AS book_id,
                   b.title AS book_title,
                   c.barcode AS copy_barcode,
                   li.due_date
            FROM loan_items li
            JOIN loans l ON l.id = li.loan_id
            JOIN users u ON u.id = l.borrower_user_id
            LEFT JOIN library_cards lc ON lc.user_id = u.id
            JOIN book_copies c ON c.id = li.book_copy_id
            JOIN books b ON b.id = c.book_id
            WHERE li.returned_at IS NULL
              AND (li.resolution_reason IS NULL OR li.resolution_reason NOT IN ('RETURNED', 'LOST', 'DAMAGED'))
              AND (c.status = 'BORROWED')
              AND ((li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = ?
                OR (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date <= ?)
            ORDER BY l.borrower_user_id ASC, li.due_date ASC, li.id ASC
            """;

        return jdbc.query(sql, (rs, rowNum) -> new EligibleLoanItemRow(
                rs.getLong("item_id"),
                rs.getLong("loan_id"),
                rs.getString("loan_number"),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                rs.getString("reader_card_number"),
                rs.getString("reader_email"),
                rs.getLong("book_id"),
                rs.getString("book_title"),
                rs.getString("copy_barcode"),
                rs.getObject("due_date", OffsetDateTime.class)
        ), Date.valueOf(in2Days), Date.valueOf(targetDate));
    }

    public boolean isAlreadySentToday(LocalDate reminderDate, Long readerId, String reminderGroup) {
        return !"READY".equals(deliveryState(reminderDate, readerId, reminderGroup));
    }

    public Long insertLog(
            LocalDate reminderDate,
            Long readerId,
            String readerName,
            String readerCardNumber,
            String recipientEmail,
            String reminderGroup,
            int loanItemCount,
            long totalEstimatedFeeVnd,
            int retryCount,
            int maxRetries,
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
                reminder_date, reader_id, reader_name, reader_card_number, recipient_email,
                reminder_group, loan_item_count, total_estimated_fee_vnd, retry_count, max_retries,
                loan_numbers, book_titles, items_detail, subject, email_content, status, error_message,
                triggered_by, triggered_by_user_id, triggered_by_user_name, is_dry_run, sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setDate(1, Date.valueOf(reminderDate));
            ps.setLong(2, readerId);
            ps.setString(3, readerName);
            ps.setString(4, readerCardNumber);
            ps.setString(5, recipientEmail);
            ps.setString(6, reminderGroup);
            ps.setInt(7, loanItemCount);
            ps.setLong(8, totalEstimatedFeeVnd);
            ps.setInt(9, retryCount);
            ps.setInt(10, maxRetries);
            ps.setString(11, loanNumbers);
            ps.setString(12, bookTitles);
            ps.setString(13, itemsDetailJson != null ? itemsDetailJson : "[]");
            ps.setString(14, subject);
            ps.setString(15, emailContent);
            ps.setString(16, status);
            ps.setString(17, errorMessage);
            ps.setString(18, triggeredBy);
            if (triggeredByUserId != null) {
                ps.setLong(19, triggeredByUserId);
            } else {
                ps.setNull(19, java.sql.Types.BIGINT);
            }
            ps.setString(20, triggeredByUserName);
            ps.setBoolean(21, isDryRun);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    // Overload for backward compatibility in existing tests
    public Long insertLog(
            LocalDate reminderDate, Long readerId, String readerName, String recipientEmail,
            String reminderGroup, int loanItemCount, long totalEstimatedFeeVnd, String loanNumbers,
            String bookTitles, String itemsDetailJson, String subject, String emailContent,
            String status, String errorMessage, String triggeredBy, Long triggeredByUserId,
            String triggeredByUserName, boolean isDryRun
    ) {
        return insertLog(
                reminderDate, readerId, readerName, null, recipientEmail,
                reminderGroup, loanItemCount, totalEstimatedFeeVnd, 0, 3,
                loanNumbers, bookTitles, itemsDetailJson, subject, emailContent,
                status, errorMessage, triggeredBy, triggeredByUserId, triggeredByUserName, isDryRun
        );
    }

    public void insertRetryAttempt(
            Long reminderLogId,
            int attemptNumber,
            String status,
            String errorMessage,
            String triggeredBy
    ) {
        String sql = """
            INSERT INTO loan_reminder_retry_attempts (
                reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbc.update(sql, reminderLogId, attemptNumber, status, errorMessage, triggeredBy);
    }

    public List<LoanReminderRetryAttemptDto> findAttemptsForLog(Long reminderLogId) {
        String sql = """
            SELECT id, reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            FROM loan_reminder_retry_attempts
            WHERE reminder_log_id = ?
            ORDER BY attempt_number ASC
            """;
        return jdbc.query(sql, (rs, rowNum) -> new LoanReminderRetryAttemptDto(
                rs.getLong("id"),
                rs.getLong("reminder_log_id"),
                rs.getInt("attempt_number"),
                rs.getObject("attempted_at", OffsetDateTime.class),
                rs.getString("status"),
                com.duanttcsn5.library.service.LoanReminderFailureMessages.message(rs.getString("error_message")),
                rs.getString("triggered_by")
        ), reminderLogId);
    }

    public Map<Long, List<LoanReminderRetryAttemptDto>> findAttemptsForLogs(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) return Collections.emptyMap();
        String inSql = String.join(",", Collections.nCopies(logIds.size(), "?"));
        String sql = String.format("""
            SELECT id, reminder_log_id, attempt_number, status, error_message, triggered_by, attempted_at
            FROM loan_reminder_retry_attempts
            WHERE reminder_log_id IN (%s)
            ORDER BY reminder_log_id ASC, attempt_number ASC
            """, inSql);

        Map<Long, List<LoanReminderRetryAttemptDto>> map = new LinkedHashMap<>();
        jdbc.query(sql, (rs) -> {
            Long logId = rs.getLong("reminder_log_id");
            LoanReminderRetryAttemptDto dto = new LoanReminderRetryAttemptDto(
                    rs.getLong("id"),
                    logId,
                    rs.getInt("attempt_number"),
                    rs.getObject("attempted_at", OffsetDateTime.class),
                    rs.getString("status"),
                    com.duanttcsn5.library.service.LoanReminderFailureMessages.message(rs.getString("error_message")),
                    rs.getString("triggered_by")
            );
            map.computeIfAbsent(logId, k -> new ArrayList<>()).add(dto);
        }, logIds.toArray());
        return map;
    }

    public void updateLogRetryStatus(Long logId, String newStatus, int retryCount, String lastError) {
        String sql = """
            UPDATE loan_reminder_logs
            SET status = ?, retry_count = ?, error_message = ?, sent_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        jdbc.update(sql, newStatus, retryCount, lastError, logId);
    }

    public Optional<LoanReminderLogResponse> findLogById(Long id) {
        String sql = "SELECT * FROM loan_reminder_logs WHERE id = ?";
        List<LoanReminderLogResponse> list = jdbc.query(sql, (rs, rowNum) -> mapLogRow(rs), id);
        if (list.isEmpty()) return Optional.empty();
        LoanReminderLogResponse base = list.get(0);
        List<LoanReminderRetryAttemptDto> attempts = findAttemptsForLog(id);
        return Optional.of(new LoanReminderLogResponse(
                base.id(), base.reminderDate(), base.readerId(), base.readerName(),
                base.readerCardNumber(), base.recipientEmail(), base.reminderGroup(),
                base.loanItemCount(), base.totalEstimatedFeeVnd(), base.retryCount(),
                base.maxRetries(), base.loanNumbers(), base.bookTitles(), base.itemsDetail(),
                base.subject(), base.emailContent(), base.status(), base.errorMessage(),
                base.triggeredBy(), base.triggeredByUserName(), base.isDryRun(),
                base.sentAt(), attempts, base.deliveryState()
        ));
    }

    public List<LoanReminderLogResponse> findPendingRetryLogs() {
        String sql = """
            SELECT * FROM loan_reminder_logs
            WHERE status = 'PENDING_RETRY' AND retry_count < max_retries AND is_dry_run = FALSE
            ORDER BY sent_at ASC, id ASC
            """;
        return jdbc.query(sql, (rs, rowNum) -> mapLogRow(rs));
    }

    public List<LoanReminderLogResponse> findLogs(
            LocalDate date,
            String reminderGroup,
            String status,
            String search,
            String cardNumber,
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
            if ("OVERDUE_DAILY".equals(reminderGroup.trim())) {
                sql.append("AND reminder_group IN ('OVERDUE_DAILY','OVERDUE_1_DAY','OVERDUE_7_DAYS','OVERDUE_30_DAYS') ");
            } else {
                sql.append("AND reminder_group = ? ");
                params.add(reminderGroup.trim());
            }
        }
        if (status != null && !status.isBlank()) {
            if ("FAILED".equals(status.trim())) {
                sql.append("AND status <> 'SENT' ");
            } else {
                sql.append("AND status = ? ");
                params.add(status.trim());
            }
        }
        if (cardNumber != null && !cardNumber.isBlank()) {
            sql.append("AND reader_card_number ILIKE ? ");
            params.add("%" + cardNumber.trim() + "%");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (reader_name ILIKE ? OR recipient_email ILIKE ? OR reader_card_number ILIKE ? OR loan_numbers ILIKE ?) ");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        sql.append("ORDER BY sent_at DESC, id DESC OFFSET ? LIMIT ?");
        params.add(Math.max(0, offset));
        params.add(Math.max(1, limit));

        List<LoanReminderLogResponse> logs = jdbc.query(sql.toString(), (rs, rowNum) -> mapLogRow(rs), params.toArray());
        if (logs.isEmpty()) return logs;

        List<Long> ids = logs.stream().map(LoanReminderLogResponse::id).toList();
        Map<Long, List<LoanReminderRetryAttemptDto>> attemptsMap = findAttemptsForLogs(ids);

        return logs.stream().map(l -> new LoanReminderLogResponse(
                l.id(), l.reminderDate(), l.readerId(), l.readerName(),
                l.readerCardNumber(), l.recipientEmail(), l.reminderGroup(),
                l.loanItemCount(), l.totalEstimatedFeeVnd(), l.retryCount(),
                l.maxRetries(), l.loanNumbers(), l.bookTitles(), l.itemsDetail(),
                l.subject(), l.emailContent(), l.status(), l.errorMessage(),
                l.triggeredBy(), l.triggeredByUserName(), l.isDryRun(),
                l.sentAt(), attemptsMap.getOrDefault(l.id(), List.of()), l.deliveryState()
        )).toList();
    }

    public List<LoanReminderLogResponse> findLogs(LocalDate date, String reminderGroup, String status, int offset, int limit) {
        return findLogs(date, reminderGroup, status, null, null, offset, limit);
    }

    public long countLogs(LocalDate date, String reminderGroup, String status, String search, String cardNumber) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM loan_reminder_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND reminder_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (reminderGroup != null && !reminderGroup.isBlank()) {
            if ("OVERDUE_DAILY".equals(reminderGroup.trim())) {
                sql.append("AND reminder_group IN ('OVERDUE_DAILY','OVERDUE_1_DAY','OVERDUE_7_DAYS','OVERDUE_30_DAYS') ");
            } else {
                sql.append("AND reminder_group = ? ");
                params.add(reminderGroup.trim());
            }
        }
        if (status != null && !status.isBlank()) {
            if ("FAILED".equals(status.trim())) {
                sql.append("AND status <> 'SENT' ");
            } else {
                sql.append("AND status = ? ");
                params.add(status.trim());
            }
        }
        if (cardNumber != null && !cardNumber.isBlank()) {
            sql.append("AND reader_card_number ILIKE ? ");
            params.add("%" + cardNumber.trim() + "%");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (reader_name ILIKE ? OR recipient_email ILIKE ? OR reader_card_number ILIKE ? OR loan_numbers ILIKE ?) ");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    public long countLogs(LocalDate date, String reminderGroup, String status) {
        return countLogs(date, reminderGroup, status, null, null);
    }

    private LoanReminderLogResponse mapLogRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String cardNum = null;
        try {
            cardNum = rs.getString("reader_card_number");
        } catch (Exception ignored) {}

        int retryCnt = 0;
        try {
            retryCnt = rs.getInt("retry_count");
        } catch (Exception ignored) {}

        int maxRetr = 3;
        try {
            maxRetr = rs.getInt("max_retries");
            if (maxRetr <= 0) maxRetr = 3;
        } catch (Exception ignored) {}

        return new LoanReminderLogResponse(
                rs.getLong("id"),
                rs.getDate("reminder_date").toLocalDate(),
                rs.getLong("reader_id"),
                rs.getString("reader_name"),
                cardNum,
                rs.getString("recipient_email"),
                rs.getString("reminder_group"),
                rs.getInt("loan_item_count"),
                rs.getLong("total_estimated_fee_vnd"),
                retryCnt,
                maxRetr,
                rs.getString("loan_numbers"),
                rs.getString("book_titles"),
                rs.getString("items_detail"),
                rs.getString("subject"),
                rs.getString("email_content"),
                rs.getString("status"),
                com.duanttcsn5.library.service.LoanReminderFailureMessages.message(rs.getString("error_message")),
                rs.getString("triggered_by"),
                rs.getString("triggered_by_user_name"),
                rs.getBoolean("is_dry_run"),
                rs.getObject("sent_at", OffsetDateTime.class),
                List.of(),
                rs.getBoolean("is_dry_run") ? "SIMULATION" : deliveryState(rs.getDate("reminder_date").toLocalDate(),
                        rs.getLong("reader_id"), rs.getString("reminder_group"))
        );
    }
}
