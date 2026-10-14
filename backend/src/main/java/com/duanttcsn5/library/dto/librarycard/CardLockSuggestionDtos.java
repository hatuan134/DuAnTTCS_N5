package com.duanttcsn5.library.dto.librarycard;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CardLockSuggestionDtos {
    private CardLockSuggestionDtos() {}
    public record Settings(long debtThresholdVnd, OffsetDateTime updatedAt, Long updatedBy,
                           OffsetDateTime lastCheckedAt) {}
    public record UpdateSettings(@NotNull(message="Bắt buộc nhập ngưỡng nợ phí.")
            @DecimalMin(value="0", message="Ngưỡng nợ không được âm.")
            @DecimalMax(value="1000000000000", message="Ngưỡng nợ tối đa 1.000.000.000.000 VND.")
            @Digits(integer=13, fraction=0, message="Ngưỡng nợ phải là số nguyên VND.") BigDecimal debtThresholdVnd) {}
    public record Ignore(@NotBlank(message="Bắt buộc nhập lý do bỏ qua.")
            @Size(max=1000, message="Lý do tối đa 1000 ký tự.") String reason) {}
    public record OverdueItem(Long loanId, String loanNumber, Long itemId, String bookTitle,
                              String barcode, OffsetDateTime dueAt, long overdueDays) {}
    public record OutstandingFee(Long id, String description, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal originalVnd,
                                 @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal paidVnd, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal waivedVnd, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal outstandingVnd) {}
    public record Evidence(List<OverdueItem> overdueItems, List<OutstandingFee> fees) {}
    public record Suggestion(Long id, Long cardId, String cardNumber, String readerName,
            long maxOverdueDays, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal unpaidFeesVnd, long debtThresholdVnd, String reasonCode,
            String status, boolean episodeOpen, OffsetDateTime detectedAt, OffsetDateTime updatedAt,
            Long decidedBy, String deciderName, OffsetDateTime decidedAt, String decisionReason,
            String cardStatusAfterDecision, OffsetDateTime lockEndsAt, OffsetDateTime resolvedAt,
            String currentCardStatus) {}
    public record Page(List<Suggestion> items, int page, int size, long totalItems, long totalPages,
                       Settings settings) {}
    public record Detail(Suggestion suggestion, LibraryCardResponse card, Evidence evidence,
                         Evidence currentEvidence, long currentMaxOverdueDays, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal currentUnpaidFeesVnd,
                         long currentDebtThresholdVnd, String currentReasonCode) {}
    public record Decision(Suggestion suggestion, LibraryCardResponse card, boolean accepted, String message) {}
}
