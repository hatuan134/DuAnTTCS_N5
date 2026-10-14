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
}
