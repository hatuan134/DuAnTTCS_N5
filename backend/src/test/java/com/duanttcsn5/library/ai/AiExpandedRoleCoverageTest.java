package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiExpandedRoleCoverageTest {
    private static Set<String> names(AiTools tools) {
        Set<String> result = new HashSet<>();
        for (var d : tools.definitions()) result.add((String) ((Map<?, ?>) d.get("function")).get("name"));
        return result;
    }
    @Test void privateDomainToolsAreNotExposedToGuestsOrReaders() {
        var access=mock(AiAccess.class);
        var navigation=mock(AiNavigation.class);
        var tools=new AiTools(access,navigation,null,null,null,null,null,null,null,null,null,null);
        when(access.role()).thenReturn("PUBLIC");
        assertThat(names(tools)).contains("searchBooks").doesNotContain("getReminderSummary", "getCompensationFactors", "getOverdueDebtSummary", "getPendingFeeReductions", "getMyProfileOverview");
        when(access.role()).thenReturn("READER");
        assertThat(names(tools)).contains("getMyProfileOverview").doesNotContain("getReminderSummary", "getOverdueDebtSummary", "getPendingFeeReductions");
    }
    @Test void managerAndAdminFeeReductionsRespectCurrentDomainServicePolicy() {
        var access=mock(AiAccess.class);
        var tools=new AiTools(access,mock(AiNavigation.class),null,null,null,null,null,null,null,null,null,null);
        when(access.role()).thenReturn("LIBRARIAN");
        assertThat(names(tools)).contains("getReminderSummary","getCompensationFactors").doesNotContain("getPendingFeeReductions","getOverdueDebtSummary");
        when(access.role()).thenReturn("LIBRARY_MANAGER");
        assertThat(names(tools)).contains("getPendingFeeReductions","getOverdueDebtSummary");
        when(access.role()).thenReturn("ADMIN");
        assertThat(names(tools)).contains("getOverdueDebtSummary","getPendingFeeReductions");
    }
}
