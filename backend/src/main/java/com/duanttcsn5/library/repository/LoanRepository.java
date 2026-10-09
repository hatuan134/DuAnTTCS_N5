package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.loan.ConfirmReturnResponse;

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

    public record ReturnLookupRow(Long copyId, String barcode, String bookTitle,
                                  Long loanId, String loanNumber, Long itemId,
                                  Long readerId, String readerName,
                                  OffsetDateTime borrowedAt, OffsetDateTime dueAt) {}

    /** One statement distinguishes an absent barcode from an existing, unborrowed copy.
     * The partial unique index ux_copy_unreturned_loan guarantees at most one open item.
     * Do not infer the borrower from copy status or from a historical returned item.
     */
    public Optional<ReturnLookupRow> findReturnLookup(String barcode) {
        return jdbc.query("""
                SELECT c.id AS copy_id, c.barcode, b.title, li.id AS item_id,
                       l.id AS loan_id, l.loan_number, l.borrower_user_id,
                       reader.full_name AS reader_name, li.borrowed_at, li.due_date
                FROM book_copies c
                JOIN books b ON b.id = c.book_id
                LEFT JOIN loan_items li ON li.book_copy_id = c.id AND li.returned_at IS NULL
                LEFT JOIN loans l ON l.id = li.loan_id
                LEFT JOIN users reader ON reader.id = l.borrower_user_id
                WHERE c.barcode = ?
                """, (rs, index) -> new ReturnLookupRow(rs.getLong("copy_id"),
                rs.getString("barcode"), rs.getString("title"), rs.getObject("loan_id", Long.class),
                rs.getString("loan_number"), rs.getObject("item_id", Long.class),
                rs.getObject("borrower_user_id", Long.class), rs.getString("reader_name"),
                rs.getObject("borrowed_at", OffsetDateTime.class),
                rs.getObject("due_date", OffsetDateTime.class)), barcode).stream().findFirst();
    }

    public record ReturnCandidate(Long itemId, Long loanId, Long copyId,
                                  OffsetDateTime borrowedAt, OffsetDateTime returnedAt) {}

    /** Lock the title before any loan, reservation or copy row, matching reserve/cancel/pickup. */
    public Optional<Long> lockTitleForReturn(Long itemId, String barcode) {
        return jdbc.query("""
                SELECT b.id FROM loan_items li
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                WHERE li.id = ? AND c.barcode = ?
                FOR UPDATE OF b
                """, (rs, index) -> rs.getLong("id"), itemId, barcode).stream().findFirst();
    }

    /** Do not use SKIP LOCKED: a busy FIFO head must not lose its turn. */
    public void lockPendingQueueForReturn(Long bookId) {
        jdbc.query("""
                SELECT id FROM book_reservations
                WHERE book_id = ? AND status = 'PENDING'
                ORDER BY reserved_at, id FOR UPDATE
                """, rs -> { }, bookId);
    }

    public record ReturnReservation(Long id, String readerName, OffsetDateTime reservedAt) {}

    /** Same allocation policy as S3-06.2: skip inactive accounts and missing/locked/expired
     * cards, but keep their PENDING rows and original FIFO timestamps untouched.
     * The expiry date itself remains valid. Read eligibility after all return lock waits.
     */
    public Optional<ReturnReservation> findEligiblePendingForReturn(Long bookId, LocalDate today) {
        return jdbc.query("""
                SELECT r.id, reader.full_name, r.reserved_at
                FROM book_reservations r JOIN users reader ON reader.id = r.reader_id
                WHERE r.book_id = ? AND r.status = 'PENDING' AND r.book_copy_id IS NULL
                  AND reader.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM library_cards card
                      WHERE card.user_id = r.reader_id AND card.status = 'ACTIVE'
                        AND card.expires_at >= ?)
                  AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.reservation_id = r.id)
                ORDER BY r.reserved_at, r.id LIMIT 1
                """, (rs, index) -> new ReturnReservation(rs.getLong("id"), rs.getString("full_name"),
                rs.getObject("reserved_at", OffsetDateTime.class)), bookId, today).stream().findFirst();
    }

    /** Assign before the return trigger runs, so the copy goes BORROWED -> HELD directly.
     * ux_reservations_ready_copy (V20) is the final guard against duplicate allocation.
     */
    public int allocateReturnedCopy(Long reservationId, Long bookId, Long copyId,
                                   OffsetDateTime startedAt, OffsetDateTime deadline) {
        return jdbc.update("""
                UPDATE book_reservations SET status = 'READY_FOR_PICKUP', book_copy_id = ?,
                    hold_started_at = ?, pickup_deadline = ?
                WHERE id = ? AND book_id = ? AND status = 'PENDING' AND book_copy_id IS NULL
                """, copyId, startedAt, deadline, reservationId, bookId);
    }

    /** Same loan/item locks as renewal. Read returned rows too, to reject repeated confirmations.
     * The item id and immutable barcode must both match the preview shown to staff.
     */
    public Optional<ReturnCandidate> lockReturnCandidate(Long itemId, String barcode) {
        return jdbc.query("""
                SELECT li.id, li.loan_id, li.book_copy_id, li.borrowed_at, li.returned_at
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN book_copies c ON c.id = li.book_copy_id
                WHERE li.id = ? AND c.barcode = ?
                FOR UPDATE OF li, l
                """, (rs, index) -> new ReturnCandidate(rs.getLong("id"), rs.getLong("loan_id"),
                rs.getLong("book_copy_id"), rs.getObject("borrowed_at", OffsetDateTime.class),
                rs.getObject("returned_at", OffsetDateTime.class)), itemId, barcode).stream().findFirst();
    }

    public Optional<String> lockCopyForReturn(Long copyId) {
        return jdbc.query("SELECT status FROM book_copies WHERE id = ? FOR UPDATE",
                (rs, index) -> rs.getString("status"), copyId).stream().findFirst();
    }

    /** V33 extends V24's trigger: the preallocated queue order yields HELD,
     * otherwise AVAILABLE. Both the receiver stamp and copy update are atomic.
     */
    public int markReturned(Long itemId, Long copyId, OffsetDateTime returnedAt, Long actorId) {
        return jdbc.update("""
                UPDATE loan_items li
                SET returned_at = ?, returned_by = staff.id, returned_by_name = staff.full_name
                FROM users staff JOIN roles r ON r.id = staff.role_id
                WHERE li.id = ? AND li.book_copy_id = ? AND li.returned_at IS NULL
                  AND staff.id = ? AND staff.status = 'ACTIVE'
                  AND r.code IN ('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')
                """, returnedAt, itemId, copyId, actorId);
    }

    public Optional<ConfirmReturnResponse> findReturnConfirmation(Long itemId) {
        return jdbc.query("""
                SELECT li.id, li.loan_id, li.returned_at, li.returned_by, l.loan_number,
                       c.id AS copy_id, c.barcode, c.status AS copy_status, b.title,
                       li.returned_by_name, assigned.id AS next_reservation_id,
                       waiting_reader.full_name AS next_reader_name,
                       assigned.hold_started_at, assigned.pickup_deadline,
                       CASE WHEN EXISTS (SELECT 1 FROM loan_items outstanding
                            WHERE outstanding.loan_id = l.id AND outstanding.returned_at IS NULL)
                            THEN 'BORROWED' ELSE 'RETURNED' END AS loan_status
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                LEFT JOIN book_reservations assigned ON assigned.book_copy_id = c.id
                    AND assigned.status = 'READY_FOR_PICKUP'
                    AND assigned.hold_started_at = li.returned_at
                LEFT JOIN users waiting_reader ON waiting_reader.id = assigned.reader_id
                WHERE li.id = ? AND li.returned_at IS NOT NULL
                """, (rs, index) -> new ConfirmReturnResponse(
                "HELD".equals(rs.getString("copy_status"))
                        ? "Nhận trả sách thành công. Bản sao được giữ cho " + rs.getString("next_reader_name")
                            + ", đơn #" + rs.getLong("next_reservation_id") + " đang Chờ nhận."
                        : "Nhận trả sách thành công. Bản sao đã về Sẵn sàng.",
                rs.getLong("copy_id"), rs.getString("barcode"),
                rs.getString("title"), rs.getLong("loan_id"), rs.getString("loan_number"),
                rs.getLong("id"), "RETURNED", rs.getString("loan_status"), rs.getString("copy_status"),
                rs.getObject("returned_at", OffsetDateTime.class), rs.getLong("returned_by"),
                rs.getString("returned_by_name"), rs.getObject("next_reservation_id", Long.class),
                rs.getString("next_reader_name"), rs.getObject("hold_started_at", OffsetDateTime.class),
                rs.getObject("pickup_deadline", OffsetDateTime.class)), itemId).stream().findFirst();
    }

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

    /** Count other overdue loans, excluding the entire loan being renewed (not just its item).
     *  A partially returned loan still counts while any other outstanding copy is overdue.
     */
    public long countOtherOverdueUnreturnedLoansForReader(Long readerId, Long currentLoanId, LocalDate today) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.id)
                FROM loans l
                JOIN loan_items li ON li.loan_id = l.id
                WHERE l.borrower_user_id = ?
                  AND l.id <> ?
                  AND li.returned_at IS NULL
                  AND (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date < ?
                """, Long.class, readerId, currentLoanId, today);
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

    public record RenewalPolicy(Long loanId, int renewalsUsed, Integer maxRenewals, Integer renewalDays) {}

    /** Read the quota attached to this reader's library card after locking the loan row.
     *  A missing card/policy is rejected rather than interpreted as unlimited renewals.
     */
    public Optional<RenewalPolicy> findRenewalPolicyForReader(Long itemId, Long readerId) {
        return jdbc.query("""
                SELECT l.id AS loan_id, l.renewal_count, ct.max_renewals, ct.renewal_days
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN library_cards lc ON lc.user_id = l.borrower_user_id
                JOIN card_types ct ON ct.id = lc.card_type_id
                WHERE li.id = ? AND l.borrower_user_id = ?
                FOR SHARE OF lc, ct
                """, (rs, index) -> new RenewalPolicy(rs.getLong("loan_id"),
                rs.getInt("renewal_count"), rs.getObject("max_renewals", Integer.class),
                rs.getObject("renewal_days", Integer.class)),
                itemId, readerId).stream().findFirst();
    }

    /** The caller already locked both loan and item. An unsuccessful write throws so the
     * surrounding renewal transaction rolls back the due-date change and quota together.
     */
    public void updateDueAtForRenewal(Long itemId, Long readerId,
                                     OffsetDateTime expectedDueAt, OffsetDateTime newDueAt) {
        int changed = jdbc.update("""
                UPDATE loan_items li
                SET due_date = ?
                FROM loans l
                WHERE li.id = ? AND li.loan_id = l.id AND l.borrower_user_id = ?
                  AND li.returned_at IS NULL AND li.due_date = ?
                """, newDueAt, itemId, readerId, expectedDueAt);
        if (changed != 1) {
            throw new com.duanttcsn5.library.exception.ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "RENEWAL_UPDATE_FAILED",
                    "Phiếu đã thay đổi trước khi gia hạn. Hạn trả và lượt gia hạn được giữ nguyên.");
        }
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

    /** S3-08.1: exact matches for any of the three code namespaces, including historical
     * loans for a copy. The EXISTS clause prevents duplicate loan rows when codes collide;
     * a loan's individual items are intentionally returned in full for the result display.
     */
    public record LoanSearchRow(Long loanId, String loanNumber, String cardNumber,
                                String readerName, OffsetDateTime borrowedAt,
                                Long itemId, String barcode, String bookTitle,
                                OffsetDateTime dueAt, OffsetDateTime returnedAt) {}

    public List<LoanSearchRow> searchLoansByCode(String code) {
        return jdbc.query("""
                SELECT l.id AS loan_id, l.loan_number, card.card_number,
                       reader.full_name AS reader_name, l.borrowed_at,
                       li.id AS item_id, c.barcode, b.title AS book_title,
                       li.due_date, li.returned_at
                FROM loans l
                JOIN users reader ON reader.id = l.borrower_user_id
                LEFT JOIN library_cards card ON card.user_id = l.borrower_user_id
                LEFT JOIN loan_items li ON li.loan_id = l.id
                LEFT JOIN book_copies c ON c.id = li.book_copy_id
                LEFT JOIN books b ON b.id = c.book_id
                WHERE l.loan_number = ? OR card.card_number = ?
                   OR EXISTS (
                       SELECT 1 FROM loan_items matched
                       JOIN book_copies matched_copy ON matched_copy.id = matched.book_copy_id
                       WHERE matched.loan_id = l.id AND matched_copy.barcode = ?
                   )
                ORDER BY l.borrowed_at DESC, l.id DESC, li.id ASC
                """, (rs, index) -> new LoanSearchRow(
                rs.getLong("loan_id"), rs.getString("loan_number"),
                rs.getString("card_number"), rs.getString("reader_name"),
                rs.getObject("borrowed_at", OffsetDateTime.class),
                rs.getObject("item_id", Long.class), rs.getString("barcode"),
                rs.getString("book_title"), rs.getObject("due_date", OffsetDateTime.class),
                rs.getObject("returned_at", OffsetDateTime.class)), code, code, code);
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
                       li.borrowed_at, li.due_date, li.returned_at, li.returned_by,
                       li.returned_by_name
                FROM loan_items li
                JOIN book_copies c ON c.id = li.book_copy_id
                JOIN books b ON b.id = c.book_id
                WHERE li.loan_id = ?
                ORDER BY li.id ASC
                """, (rs, index) -> new LoanDetailResponse.Item(rs.getLong("id"), rs.getLong("book_copy_id"),
                rs.getString("barcode"), rs.getLong("book_id"), rs.getString("title"),
                rs.getObject("borrowed_at", OffsetDateTime.class), rs.getObject("due_date", OffsetDateTime.class),
                rs.getObject("returned_at", OffsetDateTime.class), rs.getObject("returned_by", Long.class),
                rs.getString("returned_by_name")), loanId);
    }
}
