package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.LoanRejectionResponse;
import com.duanttcsn5.library.dto.loan.LoanRejectionPageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanRejectionRepository {
    private final JdbcTemplate jdbc;

    public LoanRejectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** The conflict path is deliberate: replaying a check never creates a second log. */
    public void save(UUID requestId, String source, Long readerId, String readerName,
                     String cardNumber, Long actorId, String actorName, Long reservationId,
                     long borrowed, int maximum, long overdue, BigDecimal unpaid,
                     List<LoanRejectionResponse.Reason> reasons) {
        List<Long> ids = jdbc.query("""
                INSERT INTO loan_rejections (request_id, source, reader_user_id, reader_name,
                    card_number, actor_user_id, actor_name, reservation_id, borrowed_books,
                    max_books, overdue_loans, unpaid_amount_vnd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (request_id) DO NOTHING RETURNING id
                """, (rs, row) -> rs.getLong("id"), requestId, source, readerId,
                readerName, cardNumber, actorId, actorName, reservationId, borrowed,
                maximum, overdue, unpaid);
        if (ids.isEmpty()) {
            // Only a retry of the same operation may be deduplicated; a reused key for a
            // different reader, staff actor, card or flow is invalid.
            Boolean sameOperation = jdbc.queryForObject("""
                    SELECT source = ? AND reader_user_id = ? AND actor_user_id = ? AND card_number = ?
                    FROM loan_rejections WHERE request_id = ?
                    """, Boolean.class, source, readerId, actorId, cardNumber, requestId);
            if (!Boolean.TRUE.equals(sameOperation)) {
                throw new com.duanttcsn5.library.exception.ApiException(
                        org.springframework.http.HttpStatus.CONFLICT, "REJECTION_REQUEST_REUSED",
                        "Mã thao tác kiểm tra đã được dùng cho giao dịch khác.");
            }
            return;
        }
        for (var reason : reasons) {
            jdbc.update("INSERT INTO loan_rejection_reasons (rejection_id, reason_code, reason_message) VALUES (?, ?, ?)",
                    ids.get(0), reason.code(), reason.message());
        }
    }

    /** Must run inside the successful loan's transaction; roll back audit with the loan. */
    public void saveOverride(UUID requestId, String source, Long readerId, String readerName,
            String cardNumber, Long actorId, String actorName, Long reservationId,
            long borrowed, int maximum, long overdue, BigDecimal unpaid,
            Long loanId, String overrideReason, List<LoanRejectionResponse.Reason> reasons) {
        Long id = jdbc.queryForObject("""
                INSERT INTO loan_rejections (request_id, source, reader_user_id, reader_name,
                    card_number, actor_user_id, actor_name, reservation_id, borrowed_books,
                    max_books, overdue_loans, unpaid_amount_vnd, event_type, override_reason, loan_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OVERRIDDEN', ?, ?)
                RETURNING id
                """, Long.class, requestId, source, readerId, readerName, cardNumber,
                actorId, actorName, reservationId, borrowed, maximum, overdue, unpaid,
                overrideReason, loanId);
        for (var reason : reasons) {
            jdbc.update("INSERT INTO loan_rejection_reasons (rejection_id, reason_code, reason_message) VALUES (?, ?, ?)",
                    id, reason.code(), reason.message());
        }
    }

    private LoanRejectionResponse map(ResultSet rs, int ignored) throws SQLException {
        return new LoanRejectionResponse(rs.getLong("id"),
                rs.getObject("occurred_at", java.time.OffsetDateTime.class), rs.getString("source"),
                rs.getLong("reader_user_id"), rs.getString("reader_name"), rs.getString("card_number"),
                rs.getLong("actor_user_id"), rs.getString("actor_name"),
                rs.getObject("reservation_id", Long.class), rs.getLong("borrowed_books"),
                rs.getInt("max_books"), rs.getLong("overdue_loans"),
                rs.getBigDecimal("unpaid_amount_vnd"), List.of(), rs.getString("event_type"),
                rs.getString("override_reason"), rs.getObject("loan_id", Long.class));
    }

    private static final String FIELDS = """
            SELECT id, occurred_at, source, reader_user_id, reader_name, card_number,
                   actor_user_id, actor_name, reservation_id, borrowed_books,
                   max_books, overdue_loans, unpaid_amount_vnd, event_type, override_reason, loan_id
            FROM loan_rejections
            """;

    public Optional<LoanRejectionResponse> findById(Long id) {
        return jdbc.query(FIELDS + " WHERE id = ?", this::map, id).stream().findFirst()
                .map(this::withReasons);
    }

    public LoanRejectionPageResponse page(String cardNumber, int page, int size) {
        String filter = cardNumber == null || cardNumber.isBlank() ? null : cardNumber.strip();
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM loan_rejections WHERE (?::text IS NULL OR card_number ILIKE '%' || ? || '%')",
                Long.class, filter, filter);
        List<LoanRejectionResponse> headers = jdbc.query(FIELDS + """
                WHERE (?::text IS NULL OR card_number ILIKE '%' || ? || '%')
                ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?
                """, this::map, filter, filter, size, (long) page * size);
        return new LoanRejectionPageResponse(headers.stream().map(this::withReasons).toList(), page, size, total);
    }

    private LoanRejectionResponse withReasons(LoanRejectionResponse header) {
        List<LoanRejectionResponse.Reason> reasons = jdbc.query("""
                SELECT reason_code, reason_message FROM loan_rejection_reasons
                WHERE rejection_id = ? ORDER BY id
                """, (rs, i) -> new LoanRejectionResponse.Reason(
                        rs.getString("reason_code"), rs.getString("reason_message")), header.id());
        return new LoanRejectionResponse(header.id(), header.occurredAt(), header.source(),
                header.readerId(), header.readerName(), header.cardNumber(), header.actorId(),
                header.actorName(), header.reservationId(), header.borrowedBooks(),
                header.maxBooks(), header.overdueLoans(), header.unpaidAmountVnd(), reasons,
                header.eventType(), header.overrideReason(), header.loanId());
    }
}
