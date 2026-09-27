package com.duanttcsn5.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "library_weekly_schedule")
public class LibraryWeeklySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week", nullable = false, unique = true)
    private int dayOfWeek;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
