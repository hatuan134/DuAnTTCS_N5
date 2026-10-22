package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

public record FeeReceiptResponse(Long id, String transactionNumber, String receiptNumber,
        Long readerId, String readerName, String cardNumber, Long collectedBy, String collectorName,
        OffsetDateTime collectedAt, long totalAmountVnd, long debtBeforeVnd, long debtAfterVnd,
        String paymentMode, List<Item> items, String fundingSource) {
    public FeeReceiptResponse(Long id, String transactionNumber, String receiptNumber,
            Long readerId, String readerName, String cardNumber, Long collectedBy, String collectorName,
            OffsetDateTime collectedAt, long totalAmountVnd, long debtBeforeVnd, long debtAfterVnd,
            String paymentMode, List<Item> items) {
        this(id,transactionNumber,receiptNumber,readerId,readerName,cardNumber,collectedBy,collectorName,
            collectedAt,totalAmountVnd,debtBeforeVnd,debtAfterVnd,paymentMode,items,"CASH");
    }
    public FeeReceiptResponse(Long id, String transactionNumber, String receiptNumber,
            Long readerId, String readerName, String cardNumber, Long collectedBy, String collectorName,
            OffsetDateTime collectedAt, long totalAmountVnd, List<Item> items) {
        this(id, transactionNumber, receiptNumber, readerId, readerName, cardNumber, collectedBy,
                collectorName, collectedAt, totalAmountVnd, totalAmountVnd, 0, "FULL", items);
    }
    public record Item(Long feeId, String feeType, String description, long originalAmountVnd,
                       long previouslyPaidVnd, long waivedAmountVnd, long collectedAmountVnd) {}
}
