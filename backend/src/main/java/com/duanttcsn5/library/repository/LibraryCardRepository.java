package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryCardRepository extends JpaRepository<LibraryCard, Long> {

    Optional<LibraryCard> findByUserId(Long userId);

    Optional<LibraryCard> findByCardNumber(String cardNumber);
}
