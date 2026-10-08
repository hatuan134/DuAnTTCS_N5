package com.duanttcsn5.library.dto.book;

import com.duanttcsn5.library.entity.ReservationAutoCancellationRun;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AutoCancellationRunResponse {

    private Long id;
    private LocalDate runDate;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String status;
    private int totalIdentified;
    private int totalCancelled;
    private int totalTransferred;
    private int totalReleased;
    private int errorCount;
    private String errorMessage;
    private String triggeredBy;
    private List<AutoCancelledReservationResponse> cancelledReservations = new ArrayList<>();

    public AutoCancellationRunResponse() {}

    public AutoCancellationRunResponse(
            Long id,
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
            String triggeredBy,
            List<AutoCancelledReservationResponse> cancelledReservations) {
        this.id = id;
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
        if (cancelledReservations != null) {
            this.cancelledReservations = cancelledReservations;
        }
    }

    public static AutoCancellationRunResponse fromEntity(
            ReservationAutoCancellationRun entity,
            List<AutoCancelledReservationResponse> details) {
        if (entity == null) {
            return null;
        }
        return new AutoCancellationRunResponse(
                entity.getId(),
                entity.getRunDate(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getStatus(),
                entity.getTotalIdentified(),
                entity.getTotalCancelled(),
                entity.getTotalTransferred(),
                entity.getTotalReleased(),
                entity.getErrorCount(),
                entity.getErrorMessage(),
                entity.getTriggeredBy(),
                details != null ? details : new ArrayList<>()
        );
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

    public List<AutoCancelledReservationResponse> getCancelledReservations() {
        return cancelledReservations;
    }

    public void setCancelledReservations(List<AutoCancelledReservationResponse> cancelledReservations) {
        this.cancelledReservations = cancelledReservations;
    }
}
