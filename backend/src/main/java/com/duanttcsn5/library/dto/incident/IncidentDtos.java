package com.duanttcsn5.library.dto.incident;

import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class IncidentDtos {
    private IncidentDtos() {}
    public record Factors(BigDecimal lost, BigDecimal damaged, OffsetDateTime updatedAt) {}
    public record Item(Long itemId, Long loanId, String loanNumber, Long readerId, String readerName,
                       Long copyId, String barcode, String bookTitle, OffsetDateTime dueAt,
                       String copyStatus, OffsetDateTime closedAt, String resolutionReason, BigDecimal coverPrice) {}
    public record Preview(Item item, String type, BigDecimal factor, long compensationVnd,
                          ReturnFeeAssessment overdueFee, String token) {}
    public record Photo(Long id, String filename, String contentType) {}
    public record Incident(Long id, Item item, String type, String description, BigDecimal coverPrice,
                           BigDecimal factor, long compensationVnd, Long compensationFeeId,
                           ReturnFeeAssessment overdueFee, String actorName, OffsetDateTime recordedAt,
                           String beforeStatus, String afterStatus, String loanStatus, List<Photo> photos,
                           long automaticCompensationVnd, String adjustmentReason, Long actorUserId) {}
    public record Image(String contentType, byte[] data) {}
}
