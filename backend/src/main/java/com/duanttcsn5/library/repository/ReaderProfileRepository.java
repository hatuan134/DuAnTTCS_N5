package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.ReaderProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {

    boolean existsByMemberCodeIgnoreCase(String memberCode);

    Optional<ReaderProfile> findByMemberCodeIgnoreCase(String memberCode);

    @Query("select rp from ReaderProfile rp join fetch rp.user u join fetch u.role order by rp.submittedAt desc")
    List<ReaderProfile> findAllWithUser();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rp from ReaderProfile rp join fetch rp.user u join fetch u.role where rp.userId = :userId")
    Optional<ReaderProfile> findByIdForUpdate(@Param("userId") Long userId);

    /*
     * S1-04: chỉ lấy hồ sơ PENDING cùng thông tin user/role.
     * Việc tìm kiếm và lọc ngày được thực hiện trong LibraryCardService bằng Java.
     * Cách này chủ động tránh hoàn toàn lỗi PostgreSQL/Hibernate với tham số nullable
     * và các biểu thức lower()/concat() đã gây SQLState 42883 / 42P18.
     */
    @Query("""
            select rp from ReaderProfile rp
            join fetch rp.user u
            join fetch u.role
            where rp.registrationStatus = 'PENDING'
            order by rp.submittedAt asc
            """)
    List<ReaderProfile> findPendingWithUser();
}
