package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.dto.book.PublicCatalogPageResponse;
import com.duanttcsn5.library.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Year means the persisted publication_year, not free-text matching against a book title. */
class AiRoleYearExpansionTest {
    private final AiToolsSecurityTest fixture = new AiToolsSecurityTest();
    private final JsonMapper json = JsonMapper.builder().build();
    @AfterEach void cleanup() { fixture.clear(); }
    @Test void publicationYearFiltersViaRealCatalogService() {
        AiToolsSecurityTest.login("PUBLIC",0);
        when(fixture.catalog.searchPublicBooks("", null, 2026, false, 0, 6, "publicationYear"))
            .thenReturn(new PublicCatalogPageResponse(List.of(),0,6,0,0,true,true,"publicationYear"));
        var result=fixture.tools.execute("searchBooks",json.readTree("{\"publicationYear\":2026}"));
        verify(fixture.catalog).searchPublicBooks("",null,2026,false,0,6,"publicationYear");
        assertThat(result.block().meta()).containsEntry("publicationYear",2026);
        assertThat(result.block().actions().get(0).href()).isEqualTo("/catalog?publicationYear=2026");
        assertThat(AiNaturalAnswer.compose("Tìm sách xuất bản năm 2026",List.of(result.block()))).contains("2026");
    }
    @Test void modelQueryTextWithPublicationYearDoesNotBecomeAnInvalidTitleKeyword() {
        AiToolsSecurityTest.login("READER",3);
        when(fixture.catalog.searchPublicBooks("",null,2026,false,0,6,"publicationYear"))
            .thenReturn(new PublicCatalogPageResponse(List.of(),0,6,0,0,true,true,"publicationYear"));
        fixture.tools.execute("searchBooks",json.readTree("{\"query\":\"sách xuất bản năm 2026\"}"));
        verify(fixture.catalog).searchPublicBooks("",null,2026,false,0,6,"publicationYear");
    }
    @Test void publicationYearCannotBeInvalidOrFuture() {
        AiToolsSecurityTest.login("PUBLIC",0);
        assertThatThrownBy(()->fixture.tools.execute("searchBooks",json.readTree("{\"publicationYear\":2200}")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(()->fixture.tools.execute("openCatalogFiltered",json.readTree("{\"publicationYear\":-1}")))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(fixture.catalog);
    }
    @ParameterizedTest @ValueSource(strings={"PUBLIC","READER","LIBRARIAN","LIBRARY_MANAGER","ADMIN"})
    void everyRoleReceivesOnlyAuthorizedFeatureCatalogue(String role) {
        AiToolsSecurityTest.login(role,22);
        var result=fixture.tools.execute("getAvailableFeatures",json.readTree("{}"));
        var titles=result.block().items().stream().map(m->m.get("title")).toList();
        assertThat(titles).contains("Tra cứu sách");
        if(role.equals("PUBLIC"))assertThat(titles).doesNotContain("Quản lý tài khoản","Tra cứu phiếu mượn");
        if(role.equals("READER"))assertThat(titles).contains("Thẻ thư viện của tôi").doesNotContain("Quản lý tài khoản");
        if(role.equals("LIBRARIAN"))assertThat(titles).contains("Nhận trả sách");
        if(role.equals("LIBRARY_MANAGER"))assertThat(titles).contains("Báo cáo mượn trả");
        if(role.equals("ADMIN"))assertThat(titles).contains("Rà soát phân quyền");
    }
    @Test void extraStaffToolsNeverBecomeReaderTools() {
        AiToolsSecurityTest.login("READER",22);
        for(var tool:List.of("getCardTypes","getWarehouseSummary","getShelves","getOverdueFeePolicy")) {
            assertThatThrownBy(()->fixture.tools.execute(tool,json.readTree("{}")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
}
