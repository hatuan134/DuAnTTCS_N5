package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LibraryCardRepository extends JpaRepository<LibraryCard, Long> {

    // Same per-reader advisory key used by loan/reservation confirmations.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(-CAST(:readerId AS bigint))", nativeQuery = true)
    Integer lockReaderForTransactions(@Param("readerId") Long readerId);

    @Query("select lc.user.id from LibraryCard lc where lc.id = :id")
    Optional<Long> findReaderId(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select lc from LibraryCard lc where lc.id = :id")
    Optional<LibraryCard> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT COALESCE(SUM(amount_vnd - paid_amount_vnd - waived_amount_vnd), 0)
            FROM reader_fees
            WHERE reader_user_id = :readerId
              AND NOT EXISTS (SELECT 1 FROM book_incidents i
                  JOIN book_incident_adjustments a ON a.incident_id = i.id
                  WHERE i.compensation_fee_id = reader_fees.id AND a.status = 'CONFIRMED')
              AND paid_amount_vnd + waived_amount_vnd < amount_vnd
            """, nativeQuery = true)
    java.math.BigDecimal sumUnpaidFees(@Param("readerId") Long readerId);

    boolean existsByCardNumber(String cardNumber);

    @Query("select lc from LibraryCard lc join fetch lc.user u join fetch u.role "
            + "join fetch lc.cardType ct where lc.cardNumber = :cardNumber")
    Optional<LibraryCard> findByCardNumberWithDetails(@Param("cardNumber") String cardNumber);

    boolean existsByUser_Id(Long userId);

    @Query("select lc from LibraryCard lc join fetch lc.user u join fetch lc.cardType ct order by lc.createdAt desc")
    List<LibraryCard> findAllWithDetails();

    @Query("select lc from LibraryCard lc join fetch lc.user u join fetch lc.cardType ct where u.id = :userId")
    Optional<LibraryCard> findByUserIdWithDetails(@Param("userId") Long userId);

    @Query(value = "SELECT full_name FROM users WHERE id = :actorId", nativeQuery = true)
    Optional<String> findLockActorName(@Param("actorId") Long actorId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO library_card_lock_history (card_id, card_number, action, actor_user_id, actor_name,
            performed_at, reason, lock_started_at, lock_ends_at, lock_reason, lock_actor_user_id,
            lock_actor_name, previous_status, resulting_status)
        VALUES (:cardId, :cardNumber, :action, :actorId,
            COALESCE((SELECT full_name FROM users WHERE id = :actorId), 'Không xác định'),
            :performedAt, :reason, :lockStartedAt, :lockEndsAt, :lockReason, :lockActorId,
            COALESCE((SELECT actor_name FROM library_card_lock_history
                WHERE card_id = :cardId AND lock_started_at = :lockStartedAt AND action = 'LOCK'),
                (SELECT full_name FROM users WHERE id = :lockActorId), 'Không xác định'),
            :previousStatus, :resultingStatus)
        """, nativeQuery = true)
    int insertLockHistory(@Param("cardId") Long cardId, @Param("cardNumber") String cardNumber,
            @Param("action") String action, @Param("actorId") Long actorId,
            @Param("performedAt") java.time.OffsetDateTime performedAt, @Param("reason") String reason,
            @Param("lockStartedAt") java.time.OffsetDateTime lockStartedAt,
            @Param("lockEndsAt") java.time.OffsetDateTime lockEndsAt, @Param("lockReason") String lockReason,
            @Param("lockActorId") Long lockActorId, @Param("previousStatus") String previousStatus,
            @Param("resultingStatus") String resultingStatus);

    @Query(value = """
        SELECT id, card_number AS "cardNumber", action, actor_user_id AS "actorUserId",
            actor_name AS "actorName", performed_at AS "performedAt", reason,
            lock_started_at AS "lockStartedAt", lock_ends_at AS "lockEndsAt", lock_reason AS "lockReason",
            lock_actor_user_id AS "lockActorUserId", lock_actor_name AS "lockActorName",
            previous_status AS "previousStatus", resulting_status AS "resultingStatus"
        FROM library_card_lock_history WHERE card_id = :cardId ORDER BY performed_at DESC, id DESC
        """, nativeQuery = true)
    List<LockHistoryRow> findLockHistory(@Param("cardId") Long cardId);

    interface LockHistoryRow {
        Long getId(); String getCardNumber(); String getAction(); Long getActorUserId(); String getActorName();
        java.time.OffsetDateTime getPerformedAt(); String getReason();
        java.time.OffsetDateTime getLockStartedAt(); java.time.OffsetDateTime getLockEndsAt(); String getLockReason();
        Long getLockActorUserId(); String getLockActorName(); String getPreviousStatus(); String getResultingStatus();
    }
}
