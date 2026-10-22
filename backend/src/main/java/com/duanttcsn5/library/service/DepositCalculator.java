package com.duanttcsn5.library.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;

/** Pure deposit arithmetic. Activation, authorization and recorded cash belong to the transaction service. */
public final class DepositCalculator {
    private DepositCalculator() {}
    private static final long MAX_EXACT_JSON_INTEGER = 9_007_199_254_740_991L;
    public record CopyPrice(Long copyId, BigDecimal coverPrice) {}
    public record Allocation(Long copyId, BigDecimal coverPrice, BigDecimal percentage, long amountVnd) {}
    public record Quote(List<Allocation> allocations, long totalVnd) {}
    public record Reconciliation(long heldVnd, long confirmedDebtVnd, long deductibleVnd,
                                 long refundableVnd, long additionalDueVnd) {}

    /** Round each book HALF_UP to whole VND, then sum the allocations exactly. No default rate. */
    public static Quote quote(List<CopyPrice> copies, BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0
                || percentage.scale() > 4) throw new IllegalArgumentException("DEPOSIT_PERCENTAGE_REQUIRED");
        if (copies == null || copies.isEmpty() || copies.size() > 10) throw new IllegalArgumentException("DEPOSIT_COPIES_REQUIRED");
        var ids = new HashSet<Long>();
        var allocations = new java.util.ArrayList<Allocation>();
        long total = 0;
        for (var copy : copies) {
            if (copy == null || copy.copyId() == null || copy.copyId() <= 0 || !ids.add(copy.copyId()))
                throw new IllegalArgumentException("DEPOSIT_COPY_INVALID_OR_DUPLICATE");
            if (copy.coverPrice() == null || copy.coverPrice().signum() <= 0)
                throw new IllegalArgumentException("DEPOSIT_COVER_PRICE_REQUIRED");
            long amount;
            try {
                amount = copy.coverPrice().multiply(percentage).movePointLeft(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
                total = Math.addExact(total, amount);
            } catch (ArithmeticException ex) { throw new IllegalArgumentException("DEPOSIT_AMOUNT_TOO_LARGE", ex); }
            // A mandatory deposit must never silently become zero through rounding.
            if (amount <= 0) throw new IllegalArgumentException("DEPOSIT_AMOUNT_BELOW_ONE_VND");
            if (total > MAX_EXACT_JSON_INTEGER) throw new IllegalArgumentException("DEPOSIT_AMOUNT_TOO_LARGE");
            allocations.add(new Allocation(copy.copyId(), copy.coverPrice(), percentage, amount));
        }
        return new Quote(List.copyOf(allocations), total);
    }

    /** Preview only. Neither a deduction nor a refund has happened until explicitly recorded. */
    public static Reconciliation reconcile(long heldVnd, long confirmedDebtVnd) {
        if (heldVnd < 0 || confirmedDebtVnd < 0 || heldVnd > MAX_EXACT_JSON_INTEGER || confirmedDebtVnd > MAX_EXACT_JSON_INTEGER)
            throw new IllegalArgumentException("DEPOSIT_RECONCILIATION_INVALID");
        long deduction = Math.min(heldVnd, confirmedDebtVnd);
        return new Reconciliation(heldVnd, confirmedDebtVnd, deduction, heldVnd - deduction, confirmedDebtVnd - deduction);
    }
}
