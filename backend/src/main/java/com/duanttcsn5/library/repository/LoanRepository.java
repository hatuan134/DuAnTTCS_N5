package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Reuses the JDBC lending schema, as BookCopyLifecycleRepository already does. */
@Repository
public class LoanRepository {
    private final JdbcTemplate jdbc;

    public LoanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record DirectRequest(Long loanId, Long actorId, String fingerprint) {}
    public record CopyIdentity(Long copyId, Long bookId) {}

    /** Different namespace from the existing bigint reader/barcode locks. */
    public void lockDirectRequest(UUID requestId) {
        jdbc.query("SELECT pg_advisory_xact_lock(30205, hashtext(?))",
                rs -> { }, requestId.toString());
    }

    public Optional<DirectRequest> findDirectRequest(UUID requestId) {
        return jdbc.query("SELECT id, created_by, direct_request_fingerprint FROM loans WHERE direct_request_id = ?",
                (rs, index) -> new DirectRequest(rs.getLong("id"), rs.getLong("created_by"),
                        rs.getString("direct_request_fingerprint")), requestId).stream().findFirst();
    }

    public Optional<Long> findReaderIdByCardNumber(String cardNumber) {
        return jdbc.query("SELECT user_id FROM library_cards WHERE card_number = ?",
                (rs, index) -> rs.getLong("user_id"), cardNumber).stream().findFirst();
    }

    public Optional<Long> findReservationReaderId(Long reservationId) {
        return jdbc.query("SELECT reader_id FROM book_reservations WHERE id = ?",
                (rs, index) -> rs.getLong("reader_id"), reservationId).stream().findFirst();
    }

    /** Hold card/account/policy steady while validating and writing; no JPA snapshot is preloaded. */
    public Optional<Long> lockDirectLoanCard(String cardNumber) {
        return jdbc.query("""
                SELECT lc.user_id FROM library_cards lc
                JOIN users u ON u.id = lc.user_id
                JOIN card_types ct ON ct.id = lc.card_type_id
                WHERE lc.card_number = ?
                FOR SHARE OF lc, u, ct
                """, (rs, index) -> rs.getLong("user_id"), cardNumber).stream().findFirst();
    }

    /** Scalar lookup avoids caching copy status before waiting for title/copy locks. */
    public Optional<CopyIdentity> findCopyIdentity(String barcode) {
        return jdbc.query("SELECT id, book_id FROM book_copies WHERE barcode = ?",
                (rs, index) -> new CopyIdentity(rs.getLong("id"), rs.getLong("book_id")), barcode)
                .stream().findFirst();
    }

    public Long insertDirect(Long readerId, Long actorId, String loanNumber, OffsetDateTime borrowedAt,
                             UUID requestId, String fingerprint) {
        return jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at,
                                  direct_request_id, direct_request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, loanNumber, readerId, actorId, borrowedAt, requestId, fingerprint);
    }

    /** Count outstanding copies, including overdue ones, across all of this reader's loans. */
    public long countUnreturnedBooksForReader(Long readerId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(li.id)
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                WHERE l.borrower_user_id = ? AND li.returned_at IS NULL
                """, Long.class, readerId);
        return count == null ? 0 : count;
    }

    /** A loan is overdue if at least one unreturned copy has a due date before today
     *  in the library time zone. The due date itself remains a TIMESTAMPTZ.
     *  Counting distinct loans avoids reporting one loan twice for multiple overdue copies.
     */
    public long countOverdueUnreturnedLoansForReader(Long readerId, LocalDate today) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.id)
                FROM loans l
                JOIN loan_items li ON li.loan_id = l.id
                WHERE l.borrower_user_id = ?
                  AND li.returned_at IS NULL
                  AND (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date < ?
                """, Long.class, readerId, today);
        return count == null ? 0L : count;
    }

    public Optional<String> findNumberByReservation(Long reservationId) {
        return jdbc.query("SELECT loan_number FROM loans WHERE reservation_id = ?",
                (rs, index) -> rs.getString("loan_number"), reservationId).stream().findFirst();
    }

    public Long insert(Long reservationId, Long readerId, Long actorId,
                       String loanNumber, OffsetDateTime borrowedAt) {
        return jdbc.queryForObject("""
                INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at, reservation_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, loanNumber, readerId, actorId, borrowedAt, reservationId);
    }

    public void insertItem(Long loanId, Long copyId, OffsetDateTime borrowedAt) {
        insertItem(loanId, copyId, borrowedAt, null);
    }

    public void insertItem(Long loanId, Long copyId, OffsetDateTime borrowedAt, OffsetDateTime dueAt) {
        jdbc.update("""
                INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
                VALUES (?, ?, ?, ?)
                """, loanId, copyId, borrowedAt, dueAt);
    }

    public List<LoanSummaryResponse> findAllForStaff() {
        return jdbc.query("""
                SELECT l.id, l.loan_number, l.borrower_user_id, reader.full_name AS reader_name,
                       l.created_by, staff.full_name AS created_by_name, l.borrowed_at,
                       COUNT(li.id) AS item_count
                FROM loans l
                JOIN users reader ON reader.id = l.borrower_user_id
                JOIN users staff ON staff.id = l.created_by
                LEFT JOIN loan_items li ON li.loan_id = l.id
                GROUP BY l.id, reader.full_name, staff.full_name
                ORDER BY l.borrowed_at DESC, l.id DESC
                """, (rs, index) -> new LoanSummaryResponse(rs.getLong("id"), rs.getString("loan_number"),
                rs.getLong("borrower_user_id"), rs.getString("reader_name"), rs.getLong("created_by"),
                rs.getString("created_by_name"), rs.getObject("borrowed_at", OffsetDateTime.class), rs.getLong("item_count")));
    }

    public Optional<LoanDetailResponse> findHeaderForStaff(Long loanId) {
        return jdbc.query("""
                SELECT l.id, l.loan_number, l.reservation_id, l.borrower_user_id,
                       reader.full_name AS reader_name, l.created_by,
                       staff.full_name AS created_by_name, l.borrowed_at
                FROM loans l
                JOIN users reader ON reader.id = l.borrower_user_id
                JOIN users staff ON staff.id = l.created_by
                WHERE l.id = ?
                """, (rs, index) -> new LoanDetailResponse(rs.getLong("id"), rs.getString("loan_number"),
                rs.getObject("reservation_id", Long.class), rs.getLong("borrower_user_id"), rs.getString("reader_name"),
                rs.getLong("created_by"), rs.getString("created_by_name"),
                rs.getObject("borrowed_at", OffsetDateTime.class), List.of()), loanId).stream().findFirst();
    }

    public List<LoanDetailResponse.Item> findItemsForStaff(Long loanId) {
        return jdbc.query("""
                SELECT li.id, li.book_copy_id, c.barcode, c.book_id, b.title,
                       li.borrowed_at, li.due_date
                FROM loan_items li
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                WHERE li.loan_id = ?
                ORDER BY li.id ASC
                """, (rs, index) -> new LoanDetailResponse.Item(rs.getLong("id"), rs.getLong("book_copy_id"),
                rs.getString("barcode"), rs.getLong("book_id"), rs.getString("title"),
                rs.getObject("borrowed_at", OffsetDateTime.class), rs.getObject("due_date", OffsetDateTime.class)), loanId);
    }
}
