package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardTypeRepository extends JpaRepository<CardType, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<CardType> findAllByOrderByCreatedAtDesc();

    List<CardType> findAllByIsActiveTrueOrderByNameAsc();

    @Query(value = "SELECT COUNT(*) FROM library_cards WHERE card_type_id = :cardTypeId", nativeQuery = true)
    long countLibraryCardsUsingType(@Param("cardTypeId") Long cardTypeId);
}
