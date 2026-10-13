package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.IncidentCalculator;
import com.duanttcsn5.library.service.IncidentImages;
import com.duanttcsn5.library.service.LoanClosurePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookIncidentCalculationTest {
    @Test void twoFactorsAndHalfUpToOneVnd() {
        assertEquals(200000,IncidentCalculator.amount(new BigDecimal("100000"),new BigDecimal("2")));
        assertEquals(150000,IncidentCalculator.amount(new BigDecimal("100000"),new BigDecimal("1.5")));
        assertEquals(2,IncidentCalculator.amount(new BigDecimal("1"),new BigDecimal("1.5")));
        assertEquals(1,IncidentCalculator.amount(new BigDecimal("1"),new BigDecimal("1.49")));
    }
    @Test void missingPriceFactorAndOverflowAreBlocked() {
        assertEquals("MISSING_COVER_PRICE",assertThrows(ApiException.class,()->IncidentCalculator.amount(null,BigDecimal.ONE)).getCode());
        assertThrows(ApiException.class,()->IncidentCalculator.amount(BigDecimal.ZERO,BigDecimal.ONE));
        assertEquals("FACTOR_NOT_CONFIGURED",assertThrows(ApiException.class,()->IncidentCalculator.amount(BigDecimal.ONE,null)).getCode());
        assertThrows(ApiException.class,()->IncidentCalculator.validateFactor(new BigDecimal("1.234")));
        assertThrows(ApiException.class,()->IncidentCalculator.validateFactor(BigDecimal.ZERO));
        assertThrows(ApiException.class,()->IncidentCalculator.amount(new BigDecimal("9999999999"),new BigDecimal("9999999999")));
    }
    @Test void multiCopyLoanKeepsOpenUntilAllItemsResolvedAndRetainsIncidentReason() {
        assertEquals("PARTIALLY_RETURNED",LoanClosurePolicy.loan(List.of("LOST","BORROWED")));
        assertEquals("LOST",LoanClosurePolicy.loan(List.of("LOST","RETURNED")));
        assertEquals("DAMAGED",LoanClosurePolicy.loan(List.of("DAMAGED","RETURNED")));
        assertEquals("MIXED_INCIDENT",LoanClosurePolicy.loan(List.of("DAMAGED","LOST","RETURNED")));
        assertEquals("RETURNED",LoanClosurePolicy.loan(List.of("RETURNED")));
        assertEquals("EMPTY",LoanClosurePolicy.loan(List.of()));
    }
    @Test void imageLimitsAndFakeImages() {
        assertTrue(IncidentImages.validate(null).isEmpty());
        var fake=new MockMultipartFile("files","fake.png","image/png",new byte[]{1,2,3});
        assertThrows(ApiException.class,()->IncidentImages.validate(List.of(fake)));
        assertThrows(ApiException.class,()->IncidentImages.validate(List.of(fake,fake,fake,fake,fake,fake)));
        var large=new MockMultipartFile("files","big.png","image/png",new byte[IncidentImages.MAX_BYTES+1]);
        assertThrows(ApiException.class,()->IncidentImages.validate(List.of(large)));
    }
}
