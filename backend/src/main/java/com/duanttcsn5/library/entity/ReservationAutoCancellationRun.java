package com.duanttcsn5.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reservation_auto_cancellation_runs")
public class ReservationAutoCancellationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private OffsetDateTime finishedAt;

    @Column(nullable = false, length = 30)
    private String status = "SUCCESS"; // SUCCESS, PARTIAL_FAILURE, FAILED

    @Column(name = "total_identified", nullable = false)
    private int totalIdentified = 0;

    @Column(name = "total_cancelled", nullable = false)
    private int totalCancelled = 0;

    @Column(name = "total_transferred", nullable = false)
    private int totalTransferred = 0;

    @Column(name = "total_released", nullable = false)
    private int totalReleased = 0;

    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy = "SYSTEM";

    public ReservationAutoCancellationRun() {}

    public ReservationAutoCancellationRun(
            LocalDate runDate,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String status,
            int totalIdentified,
            int totalCancelled,
            int totalTransferred,
            int totalReleased,
            int errorCount,
            String errorMessage,
            String triggeredBy) {
        this.runDate = runDate;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.totalIdentified = totalIdentified;
        this.totalCancelled = totalCancelled;
        this.totalTransferred = totalTransferred;
        this.totalReleased = totalReleased;
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
        this.triggeredBy = triggeredBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalIdentified() {
        return totalIdentified;
    }

    public void setTotalIdentified(int totalIdentified) {
        this.totalIdentified = totalIdentified;
    }

    public int getTotalCancelled() {
        return totalCancelled;
    }

    public void setTotalCancelled(int totalCancelled) {
        this.totalCancelled = totalCancelled;
    }

    public int getTotalTransferred() {
        return totalTransferred;
    }

    public void setTotalTransferred(int totalTransferred) {
        this.totalTransferred = totalTransferred;
    }

    public int getTotalReleased() {
        return totalReleased;
    }

    public void setTotalReleased(int totalReleased) {
        this.totalReleased = totalReleased;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }
}
