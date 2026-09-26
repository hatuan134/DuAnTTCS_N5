package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}
