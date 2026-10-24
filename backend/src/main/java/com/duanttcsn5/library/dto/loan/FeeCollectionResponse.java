package com.duanttcsn5.library.dto.loan;

import java.time.OffsetDateTime;
import java.util.List;

public record FeeCollectionResponse(Reader reader, List<Fee> fees, long totalOutstandingVnd,
        boolean hasDebt, String debtToken, List<PaymentSummary> payments) {
    public record Reader(Long id, String fullName, String cardNumber, String memberCode,
                         String email, String phone, String cardStatus) {}
    public record Fee(Long id, String feeType, String description, long originalAmountVnd,
                      long paidAmountVnd, long waivedAmountVnd, long outstandingAmountVnd) {}
    public record PaymentSummary(Long id, String transactionNumber, String receiptNumber,
                                 OffsetDateTime collectedAt, long totalAmountVnd, String collectorName,
                                 long debtAfterVnd, String fundingSource) {
        public PaymentSummary(Long id, String transactionNumber, String receiptNumber,
                              OffsetDateTime collectedAt, long totalAmountVnd, String collectorName, long debtAfterVnd) {
            this(id, transactionNumber, receiptNumber, collectedAt, totalAmountVnd, collectorName, debtAfterVnd, "CASH");
        }
        public PaymentSummary(Long id, String transactionNumber, String receiptNumber,
                              OffsetDateTime collectedAt, long totalAmountVnd, String collectorName) {
            this(id, transactionNumber, receiptNumber, collectedAt, totalAmountVnd, collectorName, 0L, "CASH");
        }
    }
}
