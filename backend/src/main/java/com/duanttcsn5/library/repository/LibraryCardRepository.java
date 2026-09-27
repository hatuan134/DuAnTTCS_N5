package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LibraryCardRepository extends JpaRepository<LibraryCard, Long> {

    boolean existsByCardNumber(String cardNumber);

    boolean existsByUser_Id(Long userId);

    @Query("select lc from LibraryCard lc join fetch lc.user u join fetch lc.cardType ct order by lc.createdAt desc")
    List<LibraryCard> findAllWithDetails();

    @Query("select lc from LibraryCard lc join fetch lc.user u join fetch lc.cardType ct where u.id = :userId")
    Optional<LibraryCard> findByUserIdWithDetails(@Param("userId") Long userId);
}
