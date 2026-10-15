package com.duanttcsn5.library.dto.report;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OverdueDebtReportDtos {
    private OverdueDebtReportDtos() {}
    public record Reader(Long readerId, String cardNumber, String fullName, String phone,
                         long overdueBooks, long maxOverdueDays, BigDecimal outstandingVnd) {}
    public record Snapshot(OffsetDateTime snapshotAt, String defaultFilter, List<Reader> readers, int totalReaders) {}
    public record ExportLog(Long id, String actorName, OffsetDateTime requestedAt, OffsetDateTime snapshotAt,
                            OffsetDateTime completedAt, String defaultFilter, int recordCount, String status) {}
    public record History(List<ExportLog> items, int page, int size, long totalItems) {}
    public record Download(String filename, byte[] content) {}
}
