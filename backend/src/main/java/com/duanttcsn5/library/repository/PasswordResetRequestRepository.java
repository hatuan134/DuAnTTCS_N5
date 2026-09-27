package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    @Query("select r from PasswordResetRequest r join fetch r.user u join fetch u.role where r.tokenHash = :tokenHash and r.requestType = :requestType")
    Optional<PasswordResetRequest> findByTokenHashAndRequestTypeWithUser(
            @Param("tokenHash") String tokenHash,
            @Param("requestType") String requestType);

    Optional<PasswordResetRequest> findByTokenHash(String tokenHash);

    @Query("select count(r) from PasswordResetRequest r where lower(r.requestedEmail) = lower(:email) and r.requestType = :requestType and r.createdAt >= :since")
    long countByRequestedEmailIgnoreCaseAndRequestTypeAndCreatedAtAfter(
            @Param("email") String email,
            @Param("requestType") String requestType,
            @Param("since") java.time.OffsetDateTime since);
}
