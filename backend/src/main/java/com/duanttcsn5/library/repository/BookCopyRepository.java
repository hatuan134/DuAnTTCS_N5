package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookCopy;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    @EntityGraph(attributePaths = {"book", "shelf", "shelf.warehouse"})
    Optional<BookCopy> findByBarcode(String barcode);

    @Override
    @EntityGraph(attributePaths = {"book", "shelf", "shelf.warehouse"})
    Optional<BookCopy> findById(Long id);

    // PostgreSQL handles concurrent duplicate submissions without aborting this transaction.
    @Modifying
    @Query(value = """
            INSERT INTO book_copies (book_id, barcode, shelf_id, received_date, cover_price, physical_condition, status)
            VALUES (:bookId, :barcode, :shelfId, :receivedDate, :coverPrice, :physicalCondition, 'AVAILABLE')
            ON CONFLICT (barcode) DO NOTHING
            """, nativeQuery = true)
    int insertIfBarcodeAbsent(@Param("bookId") Long bookId, @Param("barcode") String barcode,
            @Param("shelfId") Long shelfId, @Param("receivedDate") LocalDate receivedDate,
            @Param("coverPrice") BigDecimal coverPrice, @Param("physicalCondition") String physicalCondition);
}
