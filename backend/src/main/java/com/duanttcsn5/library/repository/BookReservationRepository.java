package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookReservationRepository extends JpaRepository<BookReservation, Long> {

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
