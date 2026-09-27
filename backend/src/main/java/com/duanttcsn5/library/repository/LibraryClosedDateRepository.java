package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryClosedDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LibraryClosedDateRepository extends JpaRepository<LibraryClosedDate, Long> {
    boolean existsByClosedDate(LocalDate closedDate);
    boolean existsByClosedDateAndIdNot(LocalDate closedDate, Long id);
    List<LibraryClosedDate> findAllByOrderByClosedDateAsc();
    List<LibraryClosedDate> findAllByClosedDateBetweenOrderByClosedDateAsc(LocalDate start, LocalDate end);
}
