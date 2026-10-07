package com.duanttcsn5.library.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Reuses the JDBC lending schema, as BookCopyLifecycleRepository already does. */
@Repository
public class LoanRepository {
    private final JdbcTemplate jdbc;

    public LoanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
        // due_date intentionally remains NULL in S3-01.1.
        jdbc.update("""
                INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at)
                VALUES (?, ?, ?)
                """, loanId, copyId, borrowedAt);
    }
}
