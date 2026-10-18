package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

class AiFirstAgentTest {
    @Test void roleScopedRegistryExposesOnlyAuthorizedReadTools() {
        var access=mock(AiAccess.class);
        when(access.role()).thenReturn("READER");
        var tools=new AiTools(access,mock(AiNavigation.class),null,null,null,null,null,null,null,null,null,null);
        var reader=names(tools.definitions());
        assertThat(reader).contains("getMyLoans","getMyReservations","searchBooks","getAvailableFeatures");
        assertThat(reader).doesNotContain("getAuthorizedStatistics","searchStaffAccounts","searchAuthorizedReaders");
        when(access.role()).thenReturn("ADMIN");
        var admin=names(tools.definitions());
        assertThat(admin).contains("searchAuthorizedLibraryCards","getLibraryCardStatusSummary",
            "searchAuthorizedReaders","searchStaffAccounts","getAuthorizedStatistics",
            "getCardTypes","getWarehouseSummary","getOverdueFeePolicy");
        assertThat(admin).doesNotContain("getMyLoans");
        when(access.role()).thenReturn("PUBLIC");
        var publicTools=names(tools.definitions());
        assertThat(publicTools).contains("searchBooks","getAvailableFeatures");
        assertThat(publicTools).doesNotContain("getMyLoans","searchAuthorizedLibraryCards","searchStaffAccounts");
    }
    @Test void focusedToolOffersRemainScopedToTheQuestionAndRole() {
        var access=mock(AiAccess.class);
        when(access.role()).thenReturn("ADMIN");
        var tools=new AiTools(access,mock(AiNavigation.class),null,null,null,null,null,null,null,null,null,null);
        var cards=names(tools.definitionsFor("Có bao nhiêu thẻ thư viện bị khóa?","/dashboard"));
        assertThat(cards).contains("getLibraryCardStatusSummary");
        assertThat(cards).doesNotContain("getMyLoans","searchStaffAccounts");
        var year=names(tools.definitionsFor("Tìm sách xuất bản năm 2026","/catalog"));
        assertThat(year).contains("searchBooks");
        when(access.role()).thenReturn("READER");
        assertThat(names(tools.definitionsFor("Tìm sách xuất bản năm 2026","/catalog")))
            .doesNotContain("getBookCopies","getAuthorizedLoans","searchStaffAccounts");
    }
    @Test void fabricatedNumbersAreNotAcceptedAsVerifiedModelAnswers() {
        var data=List.of(new Block("overview","Số đầu sách",List.of(Map.of("title","Sách","value",510)),
            Map.of(),List.of()));
        assertThat(AiGroundingCheck.supported("LIBRA hiện có 510 đầu sách.",data)).isTrue();
        assertThat(AiGroundingCheck.supported("LIBRA hiện có 999 đầu sách.",data)).isFalse();
    }
    private Set<String> names(List<Map<String,Object>> definitions) {
        Set<String> names=new HashSet<>();
        for(var entry:definitions) names.add(Objects.toString(((Map<?,?>)entry.get("function")).get("name")));
        return names;
    }
}
