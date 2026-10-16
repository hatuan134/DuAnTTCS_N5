package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.LibraryWeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryWeeklyScheduleRepository extends JpaRepository<LibraryWeeklySchedule, Long> {
    List<LibraryWeeklySchedule> findAllByOrderByDayOfWeekAsc();
    Optional<LibraryWeeklySchedule> findByDayOfWeek(int dayOfWeek);
}
