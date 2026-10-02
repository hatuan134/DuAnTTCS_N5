package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookCopy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    @EntityGraph(attributePaths = {"book", "shelf", "shelf.warehouse"})
    Optional<BookCopy> findByBarcode(String barcode);

    @Override
    @EntityGraph(attributePaths = {"book", "shelf", "shelf.warehouse"})
    Optional<BookCopy> findById(Long id);

    @EntityGraph(attributePaths = {"book", "shelf", "shelf.warehouse"})
    List<BookCopy> findAllByBookIdOrderByIdAsc(Long bookId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BookCopy c WHERE c.id = :id")
    Optional<BookCopy> findForStatusChange(@Param("id") Long id);

    @Query(value = """
            SELECT c.book_id, COUNT(*) FROM book_copies c
            WHERE c.status = 'AVAILABLE'
              AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id = c.id AND li.returned_at IS NULL)
            GROUP BY c.book_id
            """, nativeQuery = true)
    List<Object[]> countAvailableGroupedByBookId();

    long countByBookId(Long bookId);

    @Query(value = """
            SELECT COUNT(*) FROM book_copies c WHERE c.book_id = :bookId AND c.status = 'AVAILABLE'
            AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id = c.id AND li.returned_at IS NULL)
            """, nativeQuery = true)
    long countAvailableByBookId(@Param("bookId") Long bookId);

    @Query("""
            SELECT bc.book.id, COUNT(bc.id)
            FROM BookCopy bc
            GROUP BY bc.book.id
            """)
    List<Object[]> countAllGroupedByBookId();

    @Query(value = "SELECT nextval('book_copy_barcode_seq')", nativeQuery = true)
    Long nextAutoBarcodeNumber();

    @Query(value = "SELECT set_config('app.bulk_book_copy_creation', 'true', true)", nativeQuery = true)
    String enableBulkBookCopyCreation();

    @Modifying
    @Query(value = """
            INSERT INTO book_copies (book_id, barcode, shelf_id, received_date, status)
            VALUES (:bookId, :barcode, :shelfId, :receivedDate, 'AVAILABLE')
            ON CONFLICT (barcode) DO NOTHING
            """, nativeQuery = true)
    int insertBulkGeneratedCopy(
            @Param("bookId") Long bookId,
            @Param("barcode") String barcode,
            @Param("shelfId") Long shelfId,
            @Param("receivedDate") LocalDate receivedDate
    );

    // PostgreSQL handles concurrent duplicate submissions without aborting this transaction.
    @Modifying
    @Query(value = """
            INSERT INTO book_copies (book_id, barcode, shelf_id, received_date, cover_price, physical_condition, status)
            VALUES (:bookId, :barcode, :shelfId, :receivedDate, :coverPrice, :physicalCondition, 'AVAILABLE')
            ON CONFLICT (barcode) DO NOTHING
            """, nativeQuery = true)
    int insertIfBarcodeAbsent(
            @Param("bookId") Long bookId,
            @Param("barcode") String barcode,
            @Param("shelfId") Long shelfId,
            @Param("receivedDate") LocalDate receivedDate,
            @Param("coverPrice") BigDecimal coverPrice,
            @Param("physicalCondition") String physicalCondition
    );
}
