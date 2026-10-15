package com.duanttcsn5.library.dto.report;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OverdueDebtReportDtos {
    private OverdueDebtReportDtos() {}
    public record Filter(Integer minOverdueDays, Long cardTypeId) {
        public static final Filter ALL = new Filter(null, null);
        public Filter {
            if (minOverdueDays != null && minOverdueDays < 0) throw invalidDays();
            if (cardTypeId != null && cardTypeId <= 0) throw invalidType();
        }
        public static Filter parse(String days, String type) {
            Integer minimum = null; Long id = null;
            if (days != null && !days.isBlank()) {
                if (!days.matches("[0-9]+")) throw invalidDays();
                try { minimum = Integer.valueOf(days); } catch (NumberFormatException ex) { throw invalidDays(); }
            }
            if (type != null && !type.isBlank()) {
                if (!type.matches("[0-9]+")) throw invalidType();
                try { id = Long.valueOf(type); } catch (NumberFormatException ex) { throw invalidType(); }
            }
            return new Filter(minimum, id);
        }
        private static ApiException invalidDays() {
            return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MIN_OVERDUE_DAYS",
                "Số ngày trễ tối thiểu phải là số nguyên từ 0 đến 2147483647 hoặc để trống.");
        }
        private static ApiException invalidType() {
            return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_TYPE", "Loại thẻ không hợp lệ.");
        }
        public String describe(String cardTypeName) {
            return "Số ngày trễ tối thiểu: " + (minOverdueDays == null ? "Không giới hạn" : minOverdueDays)
                + "; Loại thẻ: " + (cardTypeId == null ? "Tất cả" : cardTypeName) + ".";
        }
    }
    public record CardTypeOption(Long id, String name, boolean active) {}
    public record Reader(Long readerId, String cardNumber, String fullName, String phone,
                         long overdueBooks, long maxOverdueDays, BigDecimal outstandingVnd) {}
    public record Snapshot(OffsetDateTime snapshotAt, String defaultFilter, List<Reader> readers, int totalReaders) {}
    public record ExportLog(Long id, String actorName, OffsetDateTime requestedAt, OffsetDateTime snapshotAt,
                            OffsetDateTime completedAt, String defaultFilter, int recordCount, String status,
                            Integer minOverdueDays, Long cardTypeId, String cardTypeName) {
        public ExportLog(Long id, String actorName, OffsetDateTime requestedAt, OffsetDateTime snapshotAt,
                         OffsetDateTime completedAt, String defaultFilter, int recordCount, String status) {
            this(id, actorName, requestedAt, snapshotAt, completedAt, defaultFilter, recordCount, status, null, null, null);
        }
    }
    public record History(List<ExportLog> items, int page, int size, long totalItems) {}
    public record Download(String filename, byte[] content) {}
}
