package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface BookReservationRepository extends JpaRepository<BookReservation, Long> {

    // Only the reservation allocated to this physical copy can block its preview.
    // Keep the deadline boundary consistent with LoanService.checkPickup: equality is valid.
    // Converted legacy rows are checked by the existing loan-link logic in LoanService.
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.reader
            WHERE r.bookCopy.id = :bookCopyId
              AND r.status = 'READY_FOR_PICKUP'
              AND r.pickupDeadline >= :checkedAt
            """)
    Optional<BookReservation> findEffectiveHoldForCopy(
            @Param("bookCopyId") Long bookCopyId, @Param("checkedAt") OffsetDateTime checkedAt);

    // Positive identity user ids use negative advisory keys. This does not overlap
    // the positive barcode-sequence key. Released automatically at transaction end.
    // Serialize a reader across titles, without locking user/role rows or changing
    // the existing title -> reservation -> copy cancellation lock order.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(-CAST(:readerId AS bigint))", nativeQuery = true)
    Integer lockReaderForCreation(@Param("readerId") Long readerId);

    // Active policy: only waiting and allocated-awaiting-pickup reservations.
    // A passed pickup deadline alone does not change the persisted status.
    @Query(value = """
            SELECT COUNT(*) FROM book_reservations
            WHERE reader_id = :readerId AND status IN ('PENDING', 'READY_FOR_PICKUP')
            """, nativeQuery = true)
    long countActiveForReader(@Param("readerId") Long readerId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM book_reservations
                WHERE reader_id = :readerId AND book_id = :bookId
                  AND status IN ('PENDING', 'READY_FOR_PICKUP')
            )
            """, nativeQuery = true)
    boolean existsActiveForReaderAndBook(@Param("readerId") Long readerId, @Param("bookId") Long bookId);

    /** S3-05.2: check other readers waiting for the TITLE of the borrowed copy,
     * not just a hold assigned to the same physical copy. Completed/cancelled/expired
     * orders do not block; a READY order needs a non-expired pickup deadline.
     * The own-reader exclusion is provisional pending PO confirmation.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM loan_items li
                JOIN book_copies bc ON bc.id = li.book_copy_id
                JOIN book_reservations r ON r.book_id = bc.book_id
                WHERE li.id = :itemId
                  AND r.reader_id <> :readerId
                  AND (r.status = 'PENDING'
                       OR (r.status = 'READY_FOR_PICKUP' AND r.pickup_deadline >= :checkedAt))
            )
            """, nativeQuery = true)
    boolean existsOtherEffectiveReservationForLoanItem(@Param("itemId") Long itemId,
            @Param("readerId") Long readerId, @Param("checkedAt") OffsetDateTime checkedAt);

    // The minimal lending schema has no loan-header completion status.
    // Any unreturned item for this reader/title is authoritative, including overdue
    // loans and loans opened before a reservation. Do not infer ownership from copy status.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM loans l
                JOIN loan_items li ON li.loan_id = l.id
                JOIN book_copies bc ON bc.id = li.book_copy_id
                WHERE l.borrower_user_id = :readerId
                  AND bc.book_id = :bookId
                  AND li.returned_at IS NULL
            )
            """, nativeQuery = true)
    boolean hasUnreturnedLoanForReaderAndBook(@Param("readerId") Long readerId, @Param("bookId") Long bookId);

    // Own rows only; queue counts share the service's REPEATABLE_READ snapshot.
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            LEFT JOIN FETCH r.bookCopy
            WHERE r.reader.id = :readerId
            ORDER BY CASE r.status WHEN 'READY_FOR_PICKUP' THEN 0
                                  WHEN 'PENDING' THEN 1 ELSE 2 END,
                     CASE WHEN r.status = 'READY_FOR_PICKUP' THEN r.pickupDeadline ELSE NULL END ASC NULLS LAST,
                     r.reservedAt DESC, r.id DESC
            """)
    List<BookReservation> findAllForReader(@Param("readerId") Long readerId);

    // Read only the title id before acquiring locks; never preload a stale target entity.
    @Query("SELECT r.book.id FROM BookReservation r WHERE r.id = :id")
    Optional<Long> findBookIdForCancellation(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BookReservation r WHERE r.id = :id")
    Optional<BookReservation> findForCancellation(@Param("id") Long id);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM book_reservations r
                JOIN loan_items li
                  ON li.book_copy_id = r.book_copy_id
                 AND li.borrowed_at >= r.reserved_at
                JOIN loans l
                  ON l.id = li.loan_id
                 AND l.borrower_user_id = r.reader_id
                WHERE r.id = :reservationId
            )
            """, nativeQuery = true)
    boolean hasLoanLinkedToReservation(@Param("reservationId") Long reservationId);

    @Query(value = """
            SELECT r.* FROM book_reservations r
            WHERE r.book_id = :bookId AND r.status = 'PENDING'
            ORDER BY r.reserved_at ASC, r.id ASC
            LIMIT 1
            FOR UPDATE OF r
            """, nativeQuery = true)
    Optional<BookReservation> findNextPendingForCancellation(@Param("bookId") Long bookId);

    @Query(value = """
            SELECT r.* FROM book_reservations r
            WHERE r.book_id = :bookId AND r.status = 'PENDING'
            ORDER BY r.reserved_at ASC, r.id ASC
            FOR UPDATE OF r
            """, nativeQuery = true)
    List<BookReservation> findPendingQueueForAllocation(@Param("bookId") Long bookId);

    // One ordered snapshot of all statuses. PENDING entries receive positions
    // using the same (reservedAt, id) order as findPendingQueuePosition below.
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            JOIN FETCH r.reader
            LEFT JOIN FETCH r.bookCopy
            WHERE r.book.id = :bookId
            ORDER BY r.reservedAt ASC, r.id ASC
            """)
    List<BookReservation> findAllForQueueByBookId(@Param("bookId") Long bookId);

    // Keep legacy READY rows with no allocated copy/deadline visible. Unknown
    // deadlines belong last; id makes ordering deterministic for equal deadlines.
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            JOIN FETCH r.reader
            LEFT JOIN FETCH r.bookCopy
            WHERE r.status = 'READY_FOR_PICKUP'
            ORDER BY r.pickupDeadline ASC NULLS LAST, r.id ASC
            """)
    List<BookReservation> findReadyForPickup();

    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            JOIN FETCH r.reader
            LEFT JOIN FETCH r.bookCopy
            WHERE r.id = :reservationId AND r.status = 'READY_FOR_PICKUP'
            """)
    Optional<BookReservation> findReadyForPickupById(@Param("reservationId") Long reservationId);

    // A conversion can commit even when the client loses the POST response.
    // Keep its context readable without adding converted orders to the pickup list/detail.
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            JOIN FETCH r.reader
            LEFT JOIN FETCH r.bookCopy
            WHERE r.id = :reservationId AND r.status IN ('READY_FOR_PICKUP', 'FULFILLED', 'EXPIRED')
            """)
    Optional<BookReservation> findForLoanContext(@Param("reservationId") Long reservationId);

    // Compare against the persisted timestamp so PostgreSQL microsecond precision
    // cannot move the newly created row out of its own queue position.
    @Query(value = """
            SELECT COUNT(*)
            FROM book_reservations queued
            JOIN book_reservations target ON target.id = :reservationId
            WHERE queued.book_id = target.book_id
              AND queued.status = 'PENDING'
              AND (queued.reserved_at, queued.id) <= (target.reserved_at, target.id)
            """, nativeQuery = true)
    long findPendingQueuePosition(@Param("reservationId") Long reservationId);

    @Query(value = """
            SELECT COUNT(*) FROM book_reservations
            WHERE book_id = :bookId
              AND status = 'PENDING'
            """, nativeQuery = true)
    long countPendingQueueByBookId(@Param("bookId") Long bookId);

    @Query(value = """
            SELECT li.due_date, li.borrowed_at
            FROM loan_items li
            JOIN book_copies bc ON bc.id = li.book_copy_id
            WHERE bc.book_id = :bookId
              AND li.returned_at IS NULL
            ORDER BY li.due_date ASC NULLS LAST, li.borrowed_at ASC
            """, nativeQuery = true)
    List<Object[]> findUnreturnedLoanDatesByBookId(@Param("bookId") Long bookId);

    /**
     * S3-06.4: Quản lý tra cứu các đơn đã bị hệ thống tự động huỷ trong 30 ngày gần nhất.
     * Chỉ lấy các đơn do quy trình tự động huỷ (cancelledByName = 'Hệ thống' và lý do 'Đã huỷ do quá hạn nhận').
     * Sắp xếp các đơn bị huỷ gần nhất lên trước.
     */
    @Query("""
            SELECT r FROM BookReservation r
            JOIN FETCH r.book
            JOIN FETCH r.reader
            LEFT JOIN FETCH r.bookCopy
            LEFT JOIN FETCH r.autoCancellationRun
            WHERE r.status = 'CANCELLED'
              AND r.cancellationReason = 'Đã huỷ do quá hạn nhận'
              AND r.cancelledByName = 'Hệ thống'
              AND r.cancelledAt >= :sinceTime
            ORDER BY r.cancelledAt DESC, r.id DESC
            """)
    List<BookReservation> findAutoCancelledReservationsSince(@Param("sinceTime") OffsetDateTime sinceTime);
}
