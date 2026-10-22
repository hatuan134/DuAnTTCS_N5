package com.duanttcsn5.library.dto.loan;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DepositDtos {
    private DepositDtos() {}
    public record Policy(Long id, BigDecimal percentage, OffsetDateTime effectiveAt, OffsetDateTime createdAt,
                         Long createdBy, String creatorName, Long previousPolicyId) {}
    public record PolicyRequest(BigDecimal percentage, OffsetDateTime effectiveAt) {}
    public record Policies(Policy current, List<Policy> history, boolean activationEnabled) {
        public Policies(Policy current,List<Policy> history) {this(current,history,false);}
    }
    public record PrepareRequest(String cardNumber, List<String> barcodes) {}
    public record TransactionRequest(UUID requestKey, List<Long> accountIds, long expectedAmountVnd, String confirmation) {}
    public record Account(Long id, Long readerId, String readerName, String cardNumber, Long copyId,
                          String barcode, String bookTitle, Long policyId, BigDecimal coverPrice, BigDecimal percentage,
                          long requiredVnd, long collectedVnd, long refundedVnd, long deductedVnd, long heldVnd,
                          Long itemId, Long loanId, String loanNumber, OffsetDateTime returnedAt, long outstandingVnd,
                          OffsetDateTime createdAt) {}
    public record Accounts(List<Account> items, int page, int size, long totalItems) {}
    public record Entry(Long accountId, long amountVnd) {}
    public record Transaction(Long id, String transactionNumber, Long readerId, String kind, String method,
                              long totalVnd, Long actorId, String actorName, OffsetDateTime recordedAt,
                              Long feePaymentId, List<Entry> entries) {}
    public record Transactions(List<Transaction> items, int page, int size, long totalItems) {}
}
