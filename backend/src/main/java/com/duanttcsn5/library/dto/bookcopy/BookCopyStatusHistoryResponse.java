package com.duanttcsn5.library.dto.bookcopy;
import java.time.OffsetDateTime;
public record BookCopyStatusHistoryResponse(Long id, String previousStatus, String newStatus,
        Long actorUserId, String actorName, OffsetDateTime changedAt, String reason) {}
