package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndPasswordHash(Long userId, String passwordHash);
}
