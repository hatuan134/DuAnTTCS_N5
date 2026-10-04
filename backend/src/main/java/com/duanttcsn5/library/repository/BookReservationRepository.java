package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookReservationRepository extends JpaRepository<BookReservation, Long> {

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
}
