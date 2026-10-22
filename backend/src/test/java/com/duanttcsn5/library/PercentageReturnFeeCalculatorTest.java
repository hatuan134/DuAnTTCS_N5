package com.duanttcsn5.library;
import com.duanttcsn5.library.service.ReturnFeeCalculator;
import com.duanttcsn5.library.repository.OverdueFeeRepository.PercentageSnapshot;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.WeeklyDay;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.ClosedDay;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PercentageReturnFeeCalculatorTest {
    final OffsetDateTime due=OffsetDateTime.parse("2026-10-01T18:00:00+07:00");
    final List<WeeklyDay> week=java.util.stream.IntStream.rangeClosed(1,7).mapToObj(i->new WeeklyDay(i,true,LocalTime.of(8,0),LocalTime.of(18,0))).toList();
    PercentageSnapshot policy(String price,String rate,String cap) {return new PercentageSnapshot(1L,price==null?null:new BigDecimal(price),new BigDecimal(rate),cap==null?null:new BigDecimal(cap));}
    @Test void percentagesUseEachBooksPriceAndPreserveLegacyRounding() {
        var days=ReturnFeeCalculator.calculate(due,due.plusDays(3),0L,week,List.of());
        assertEquals(3000L,ReturnFeeCalculator.percentage(days,policy("100000","1",null)).amountVnd());
        assertEquals(6000L,ReturnFeeCalculator.percentage(days,policy("200000","1",null)).amountVnd());
        assertEquals(2000L,ReturnFeeCalculator.percentage(days,policy("50000","1",null)).amountVnd());
    }
    @Test void capNeverExceedsPerBookLimitAfterRounding() {
        var days=ReturnFeeCalculator.calculate(due,due.plusDays(3),0L,week,List.of());
        assertEquals(500L,ReturnFeeCalculator.percentage(days,policy("50000","1","1")).amountVnd());
        assertEquals(1500L,ReturnFeeCalculator.percentage(days,policy("50000","1",null)).rawAmountVnd());
    }
    @Test void closedDatesAndOnTimeArePreservedButMissingPriceIsNotZero() {
        var days=ReturnFeeCalculator.calculate(due,due.plusDays(1),0L,week,List.of(new ClosedDay(due.toLocalDate().plusDays(1),"Closed")));
        assertEquals(0L,ReturnFeeCalculator.percentage(days,policy("100000","1",null)).amountVnd());
        assertNull(ReturnFeeCalculator.percentage(days,policy(null,"1",null)).amountVnd());
        assertEquals("OVERDUE_POLICY_INVALID",ReturnFeeCalculator.percentage(days,policy("0","1",null)).status());
    }
    @Test void oneDayAndLowPricesUseDocumentedThousandDongRounding() {
        var days=ReturnFeeCalculator.calculate(due,due.plusDays(1),0L,week,List.of());
        assertEquals(1000L,ReturnFeeCalculator.percentage(days,policy("100000","1",null)).amountVnd());
        assertEquals(0L,ReturnFeeCalculator.percentage(days,policy("10000","1",null)).amountVnd());
        assertEquals(1000L,ReturnFeeCalculator.percentage(days,policy("50000","1",null)).amountVnd());
        assertEquals(100L,ReturnFeeCalculator.percentage(days,policy("10000","1",null)).rawAmountVnd());
    }
    @Test void largeAmountsAndInvalidCalendarsNeverBecomeAZeroFee() {
        var days=ReturnFeeCalculator.calculate(due,due.plusDays(365),0L,week,List.of());
        assertEquals(3650000000L,ReturnFeeCalculator.percentage(days,policy("1000000000","1",null)).amountVnd());
        assertNull(ReturnFeeCalculator.percentage(days,policy("999999999999999999999999","100",null)).amountVnd());
        var invalid=ReturnFeeCalculator.calculate(due,due.plusDays(1),0L,List.of(),List.of());
        assertEquals("WEEKLY_SCHEDULE_INVALID",ReturnFeeCalculator.percentage(invalid,policy("100000","1",null)).status());
        assertNull(ReturnFeeCalculator.percentage(invalid,policy("100000","1",null)).amountVnd());
    }
}
