package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShelfRepository extends JpaRepository<Shelf, Long> {
    boolean existsByWarehouse_IdAndCodeIgnoreCase(Long warehouseId, String code);
    boolean existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(Long warehouseId, String code, Long id);
    List<Shelf> findAllByOrderByWarehouse_NameAscCodeAsc();
    List<Shelf> findAllByWarehouse_IdOrderByCodeAsc(Long warehouseId);
    long countByWarehouse_Id(Long warehouseId);

    @Query(value = "SELECT COUNT(*) FROM book_copies WHERE shelf_id = :shelfId", nativeQuery = true)
    long countBookCopiesOnShelf(@Param("shelfId") Long shelfId);

    @Query(value = """
            SELECT COUNT(*)
            FROM book_copies bc
            JOIN shelves s ON s.id = bc.shelf_id
            WHERE s.warehouse_id = :warehouseId
            """, nativeQuery = true)
    long countBookCopiesInWarehouse(@Param("warehouseId") Long warehouseId);
}
