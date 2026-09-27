package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {

    Optional<ReaderProfile> findByUserId(Long userId);

    Optional<ReaderProfile> findByMemberCodeIgnoreCase(String memberCode);
}
