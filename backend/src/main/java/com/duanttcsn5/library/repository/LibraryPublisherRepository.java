package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LibraryPublisherRepository extends JpaRepository<LibraryPublisher, Long> {
    Optional<LibraryPublisher> findByNameIgnoreCase(String name);
}
