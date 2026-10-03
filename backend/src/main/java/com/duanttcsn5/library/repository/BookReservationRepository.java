package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookReservationRepository extends JpaRepository<BookReservation, Long> {

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
