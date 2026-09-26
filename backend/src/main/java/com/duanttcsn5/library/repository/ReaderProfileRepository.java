package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {

    boolean existsByMemberCodeIgnoreCase(String memberCode);

    Optional<ReaderProfile> findByMemberCodeIgnoreCase(String memberCode);

    @Query("select rp from ReaderProfile rp join fetch rp.user u join fetch u.role order by rp.submittedAt desc")
    List<ReaderProfile> findAllWithUser();
}
