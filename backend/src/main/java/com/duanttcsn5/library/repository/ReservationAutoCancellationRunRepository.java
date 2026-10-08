package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.ReservationAutoCancellationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationAutoCancellationRunRepository extends JpaRepository<ReservationAutoCancellationRun, Long> {

    // Lấy lần chạy gần nhất để hiển thị cho Quản lý kiểm tra
    Optional<ReservationAutoCancellationRun> findFirstByOrderByStartedAtDesc();

    // Lấy danh sách lịch sử các lần chạy gần đây
    List<ReservationAutoCancellationRun> findAllByOrderByStartedAtDesc();

    // Tìm các lần chạy trong một ngày cụ thể
    @Query("SELECT r FROM ReservationAutoCancellationRun r WHERE r.runDate = :runDate ORDER BY r.startedAt DESC")
    List<ReservationAutoCancellationRun> findByRunDate(@Param("runDate") LocalDate runDate);
}
