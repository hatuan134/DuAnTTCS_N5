package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.LoanDetailResponse;
import com.duanttcsn5.library.dto.loan.MyBorrowedBookResponse;
import com.duanttcsn5.library.dto.loan.MyReturnedBookResponse;
import com.duanttcsn5.library.dto.loan.LoanSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
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

    /** Remaining VND across every fee belonging to this reader; zero for no fees or fully paid fees. */
    public BigDecimal sumUnpaidFeesForReader(Long readerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_vnd - paid_amount_vnd), 0)
                FROM reader_fees
                WHERE reader_user_id = ? AND paid_amount_vnd < amount_vnd
                """, BigDecimal.class, readerId);
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

    /** Filter each item, so partially returned loans retain only their outstanding copies. */
    public List<MyBorrowedBookResponse> findUnreturnedForReader(Long readerId) {
        return jdbc.query("""
                SELECT li.id, b.title, c.barcode, li.borrowed_at, li.due_date,
                       l.renewal_count, ct.max_renewals
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                LEFT JOIN library_cards lc ON lc.user_id = l.borrower_user_id
                LEFT JOIN card_types ct ON ct.id = lc.card_type_id
                WHERE l.borrower_user_id = ? AND li.returned_at IS NULL
                ORDER BY li.borrowed_at DESC, li.id DESC
                """, (rs, index) -> new MyBorrowedBookResponse(rs.getLong("id"), rs.getString("title"),
                rs.getString("barcode"), rs.getObject("borrowed_at", OffsetDateTime.class),
                rs.getObject("due_date", OffsetDateTime.class), null,
                rs.getInt("renewal_count"), rs.getObject("max_renewals", Integer.class)), readerId);
    }

    public record RenewalCandidate(OffsetDateTime dueAt, OffsetDateTime returnedAt) {}

    /** Recheck a loan item owned by the authenticated reader while holding the row lock.
     *  READ COMMITTED allows a concurrent return to finish before this check reads its state.
     */
    public Optional<RenewalCandidate> findRenewalCandidateForReader(Long itemId, Long readerId) {
        return jdbc.query("""
                SELECT li.due_date, li.returned_at
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                WHERE li.id = ? AND l.borrower_user_id = ?
                FOR UPDATE OF li, l
                """, (rs, index) -> new RenewalCandidate(
                rs.getObject("due_date", OffsetDateTime.class),
                rs.getObject("returned_at", OffsetDateTime.class)), itemId, readerId)
                .stream().findFirst();
    }

    public record RenewalPolicy(Long loanId, int renewalsUsed, Integer maxRenewals) {}

    /** Read the quota attached to this reader's library card after locking the loan row.
     *  A missing card/policy is rejected rather than interpreted as unlimited renewals.
     */
    public Optional<RenewalPolicy> findRenewalPolicyForReader(Long itemId, Long readerId) {
        return jdbc.query("""
                SELECT l.id AS loan_id, l.renewal_count, ct.max_renewals
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN library_cards lc ON lc.user_id = l.borrower_user_id
                JOIN card_types ct ON ct.id = lc.card_type_id
                WHERE li.id = ? AND l.borrower_user_id = ?
                FOR SHARE OF lc, ct
                """, (rs, index) -> new RenewalPolicy(rs.getLong("loan_id"),
                rs.getInt("renewal_count"), rs.getObject("max_renewals", Integer.class)),
                itemId, readerId).stream().findFirst();
    }

    /** Conditional, atomic update. The loan row has already been locked by the check;
     *  this guard additionally protects against concurrent policy/card-type changes.
     */
    public int incrementRenewalCountIfAllowed(Long loanId, Long readerId) {
        return jdbc.update("""
                UPDATE loans l
                SET renewal_count = l.renewal_count + 1
                FROM library_cards lc
                JOIN card_types ct ON ct.id = lc.card_type_id
                WHERE l.id = ? AND l.borrower_user_id = ?
                  AND lc.user_id = l.borrower_user_id
                  AND ct.max_renewals > 0
                  AND l.renewal_count < ct.max_renewals
                """, loanId, readerId);
    }

    public long countReturnedForReader(Long readerId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(li.id)
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                WHERE l.borrower_user_id = ? AND li.returned_at IS NOT NULL
                """, Long.class, readerId);
        return count == null ? 0L : count;
    }

    /** Stable tie-breaker prevents equal return timestamps from shuffling across pages. */
    public List<MyReturnedBookResponse> findReturnedForReader(Long readerId, int limit, long offset) {
        return jdbc.query("""
                SELECT li.id, b.title, c.barcode, l.loan_number, li.borrowed_at, li.returned_at
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                WHERE l.borrower_user_id = ? AND li.returned_at IS NOT NULL
                ORDER BY li.returned_at DESC, li.id DESC
                LIMIT ? OFFSET ?
                """, (rs, index) -> new MyReturnedBookResponse(rs.getLong("id"), rs.getString("title"),
                rs.getString("barcode"), rs.getString("loan_number"),
                rs.getObject("borrowed_at", OffsetDateTime.class),
                rs.getObject("returned_at", OffsetDateTime.class)), readerId, limit, offset);
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

    /** Reader requests never load a header belonging to another account. */
    public Optional<LoanDetailResponse> findHeaderForReader(Long loanId, Long readerId) {
        return jdbc.query("""
                SELECT l.id, l.loan_number, l.reservation_id, l.borrower_user_id,
                       reader.full_name AS reader_name, l.created_by,
                       staff.full_name AS created_by_name, l.borrowed_at
                FROM loans l
                JOIN users reader ON reader.id = l.borrower_user_id
                JOIN users staff ON staff.id = l.created_by
                WHERE l.id = ? AND l.borrower_user_id = ?
                """, (rs, index) -> new LoanDetailResponse(rs.getLong("id"), rs.getString("loan_number"),
                rs.getObject("reservation_id", Long.class), rs.getLong("borrower_user_id"), rs.getString("reader_name"),
                rs.getLong("created_by"), rs.getString("created_by_name"),
                rs.getObject("borrowed_at", OffsetDateTime.class), List.of()), loanId, readerId).stream().findFirst();
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
