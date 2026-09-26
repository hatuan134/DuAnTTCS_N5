package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u join fetch u.role where lower(u.email) = lower(:email)")
    Optional<User> findForLogin(@Param("email") String email);

    @Query("select u from User u join fetch u.role order by u.createdAt desc")
    java.util.List<User> findAllWithRole();
}

