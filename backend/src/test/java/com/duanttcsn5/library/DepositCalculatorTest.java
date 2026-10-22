package com.duanttcsn5.library;

import com.duanttcsn5.library.service.DepositCalculator;
import com.duanttcsn5.library.service.DepositCalculator.CopyPrice;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepositCalculatorTest {
    CopyPrice copy(long id, String price) { return new CopyPrice(id, price == null ? null : new BigDecimal(price)); }
    @Test void multipleCopiesAreAllocatedIndividuallyBeforeTotal() {
        var result = DepositCalculator.quote(List.of(copy(1,"100000"),copy(2,"200000")),new BigDecimal("50"));
        assertEquals(150000,result.totalVnd());
        assertEquals(50000,result.allocations().get(0).amountVnd());
        assertEquals(100000,result.allocations().get(1).amountVnd());
        assertThrows(UnsupportedOperationException.class,()->result.allocations().clear());
    }
    @Test void wholeDongRoundingIsPerCopyAndNeverSilentlyZero() {
        var result = DepositCalculator.quote(List.of(copy(1,"101"),copy(2,"101")),new BigDecimal("50"));
        assertEquals(102,result.totalVnd());
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"1")),new BigDecimal("0.01")));
    }
    @Test void missingInvalidPricesRatesAndDuplicateCopiesAreRejected() {
        for (String price : new String[]{null,"0","-10"})
            assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,price)),BigDecimal.ONE));
        for (String rate : new String[]{"0","-1","101","0.00001"})
            assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"100000")),new BigDecimal(rate)));
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"100000")),null));
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"100000"),copy(1,"100000")),BigDecimal.ONE));
    }
    @Test void quotesRetainTheirPriceAndRateWithoutMutatingPreviousQuote() {
        var old = DepositCalculator.quote(List.of(copy(1,"100000")),new BigDecimal("50"));
        var next = DepositCalculator.quote(List.of(copy(1,"200000")),new BigDecimal("60"));
        assertEquals(50000,old.totalVnd()); assertEquals(120000,next.totalVnd());
        assertEquals(new BigDecimal("100000"),old.allocations().get(0).coverPrice());
    }
    @Test void returnPreviewsSeparateRefundDeductionAndAdditionalDebt() {
        var intact=DepositCalculator.reconcile(50000,0);
        assertEquals(50000,intact.refundableVnd()); assertEquals(0,intact.deductibleVnd());
        var overdue=DepositCalculator.reconcile(50000,15000);
        assertEquals(35000,overdue.refundableVnd()); assertEquals(15000,overdue.deductibleVnd());
        var lost=DepositCalculator.reconcile(50000,120000);
        assertEquals(0,lost.refundableVnd()); assertEquals(70000,lost.additionalDueVnd());
        assertEquals(50000,lost.deductibleVnd());
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.reconcile(-1,0));
    }
    @Test void overflowNeverProducesFinancialValues() {
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"999999999999999999999")),new BigDecimal("100")));
        assertThrows(IllegalArgumentException.class,()->DepositCalculator.quote(List.of(copy(1,"9007199254740992")),new BigDecimal("100")));
    }
}
