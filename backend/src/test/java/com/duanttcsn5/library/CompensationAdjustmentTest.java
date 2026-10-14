package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.IncidentCalculator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompensationAdjustmentTest {
    @Test void automaticAndEqualAmountNeedNoReason() {
        assertThat(IncidentCalculator.adjustment(300000,null,null).amount()).isEqualTo(300000);
        assertThat(IncidentCalculator.adjustment(300000,"300000"," ").reason()).isNull();
        // The PO ceiling applies to edits, not a rewrite of the existing automatic calculation.
        assertThat(IncidentCalculator.adjustment(20000000,null,null).amount()).isEqualTo(20000000);
    }
    @Test void acceptsDecreaseIncreaseZeroAndInclusiveCeiling() {
        for (long amount : new long[]{250000,350000,0,10000000}) {
            var value=IncidentCalculator.adjustment(300000,Long.toString(amount)," Sách còn phục hồi được ");
            assertThat(value.amount()).isEqualTo(amount);
            assertThat(value.reason()).isEqualTo("Sách còn phục hồi được");
        }
    }
    @Test void rejectsInvalidAmountFormatAndLimit() {
        for(String amount:new String[]{""," ","-1","1.5","1e3","NaN","250.000","250,000","99999999999999999"})
            code(amount,"Lý do","INVALID_COMPENSATION_AMOUNT");
        code("10000001","Lý do","COMPENSATION_ADJUSTMENT_LIMIT");
    }
    @Test void requiresReasonOnlyOnChange() {
        for(String reason:new String[]{null,""," \t\n","\u00a0\u202f", "x".repeat(1901)})
            code("250000",reason,"INVALID_ADJUSTMENT_REASON");
        assertThat(IncidentCalculator.adjustment(300000,"250000","x".repeat(1900)).reason()).hasSize(1900);
    }
    private void code(String amount,String reason,String expected) {
        assertThatThrownBy(()->IncidentCalculator.adjustment(300000,amount,reason))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo(expected));
    }
}
