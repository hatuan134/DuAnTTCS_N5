package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.dto.book.*;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.dto.librarycard.MyLibraryCardResponse;
import com.duanttcsn5.library.dto.report.LoanReportResponse;
import com.duanttcsn5.library.exception.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AiToolsSecurityTest {
    AiAccess access=new AiAccess();
    AiNavigation navigation=new AiNavigation(access);
    BookCatalogService catalog=mock(BookCatalogService.class);
    LoanService loans=mock(LoanService.class);
    LibraryCardService cards=mock(LibraryCardService.class);
    BookReservationService reservations=mock(BookReservationService.class);
    BookCopyService copies=mock(BookCopyService.class);
    LoanReportService reports=mock(LoanReportService.class);
    AuditLogService audit=mock(AuditLogService.class);
    BookRepository bookRepo=mock(BookRepository.class);
    BookCopyRepository copyRepo=mock(BookCopyRepository.class);
    AiReadRepository read=mock(AiReadRepository.class);
    AiTools tools=new AiTools(access,navigation,catalog,loans,cards,reservations,copies,reports,audit,bookRepo,copyRepo,read);
    static void login(String role,long id) {
        SecurityContextHolder.clearContext();
        if(!role.equals("PUBLIC"))SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new UserPrincipal(id,"test@example.invalid","Test",role,0),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    AiTypes.ToolResult run(String name,String json) { return tools.execute(name,JsonMapper.builder().build().readTree(json)); }
    @ParameterizedTest @CsvSource({"PUBLIC,getMyLoans","PUBLIC,getMyLibraryCard","PUBLIC,getMyReservations","PUBLIC,getAuthorizedLoans","PUBLIC,getAuthorizedStatistics","PUBLIC,getBookCopies","PUBLIC,getAuthorizedAudit","READER,getAuthorizedLoans","READER,getAuthorizedStatistics","READER,getBookCopies","READER,getAuthorizedAudit","LIBRARIAN,getAuthorizedStatistics","READER,openOverdueFiltered","PUBLIC,openOverdueFiltered","ADMIN,getMyLoans","LIBRARY_MANAGER,getMyLibraryCard","LIBRARIAN,getPendingFeeReductions","READER,getPendingFeeReductions","PUBLIC,getPendingFeeReductions"})
    void toolsDenyBeforeDataAccess(String role,String tool) {
        login(role,11);assertThatThrownBy(()->run(tool,"{}")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(catalog,loans,cards,reservations,copies,reports,audit,bookRepo,copyRepo,read);
    }
    @ParameterizedTest @ValueSource(strings={"PUBLIC","READER","LIBRARIAN","LIBRARY_MANAGER","ADMIN"})
    void nobodyCanExecuteArbitraryWritesOrSql(String role) {
        login(role,11);
        for(String tool:List.of("executeSql","deleteUser","reserveBook","confirmReturn","httpRequest"))assertThatThrownBy(()->run(tool,"{}")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(catalog,loans,cards,reservations,copies,reports,audit);
    }
    @ParameterizedTest @ValueSource(strings={"{\"userId\":22}","{\"readerId\":22}","{\"role\":\"ADMIN\"}","{\"page\":-1}","{\"page\":\"0\"}","{\"page\":9999999999999}","{\"url\":\"https://example.invalid\"}"})
    void toolArgumentsCannotSelectAnotherReaderOrInjectFields(String args) {
        login("READER",11);assertThatThrownBy(()->run("getMyLoans",args)).isInstanceOf(ApiException.class);verifyNoInteractions(loans,read);
    }
    @Test void adminInheritsEveryStaffAiToolButNotReaderPersonalTools() {
        login("LIBRARIAN", 11);
        Set<String> librarianTools = toolNames();
        login("LIBRARY_MANAGER", 11);
        Set<String> managerTools = toolNames();
        login("ADMIN", 11);
        Set<String> adminTools = toolNames();
        assertThat(adminTools).containsAll(librarianTools).containsAll(managerTools)
                .contains("getPendingFeeReductions");
        assertThat(adminTools).doesNotContain("getMyLoans", "getMyLibraryCard", "getMyReservations");
    }
    private Set<String> toolNames() {
        Set<String> names = new HashSet<>();
        for (var definition : tools.definitions()) {
            var function = (Map<?, ?>) definition.get("function");
            names.add((String) function.get("name"));
        }
        return names;
    }
    @Test void adminCanCallPendingFeeToolThroughExistingService() {
        login("ADMIN", 13);
        FeeReductionService reductions = mock(FeeReductionService.class);
        ReflectionTestUtils.setField(tools, "feeReductions", reductions);
        when(reductions.pending(0, 13L)).thenReturn(
                new FeeReductionRequestPageResponse(List.of(), 0, 20, 0, 0));
        var result = run("getPendingFeeReductions", "{}");
        assertThat(result.block().items()).isEmpty();
        verify(reductions).pending(0, 13L);
    }
    @Test void forgedAdminPrincipalWithWrongAuthorityCannotCallPendingTool() {
        login("ADMIN", 13);
        var adminPrincipal = access.principal();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))));
        assertThatThrownBy(() -> run("getPendingFeeReductions", "{}"))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test void readerUsesAuthenticatedIdAndOwnedLoanLink() {
        login("READER",11);
        var now=OffsetDateTime.now();
        when(loans.myBorrowedBooks(11L)).thenReturn(List.of(new MyBorrowedBookResponse(5L,"Sách A","COPY",now,now.minusDays(1),-1L,0,2)));
        when(read.ownedLoanIds(11L,List.of(5L))).thenReturn(Map.of(5L,9L));
        var out=run("getMyOverdueLoans","{}");
        assertThat(out.block().items().get(0)).containsEntry("href","/loans/9").containsEntry("overdue",true);
        assertThat(out.providerData().toString()).doesNotContain("Sách A","/loans/9");
        verify(loans).myBorrowedBooks(11L);verify(loans,never()).myBorrowedBooks(22L);
    }
    @Test void readerCardMinimizesPii() {
        login("READER",11);
        when(cards.getMyCard(11L)).thenReturn(new MyLibraryCardResponse(11L,"PRIVATE NAME","MEMBER",LocalDate.of(1990,1,1),"APPROVED",null,"CARD", "Sinh viên",LocalDate.now(),LocalDate.now().plusYears(1),"ACTIVE",null));
        var out=run("getMyLibraryCard","{}");
        assertThat(out.block().items().get(0)).containsEntry("status","ACTIVE").doesNotContainKeys("fullName","userId","dateOfBirth");
        assertThat(out.providerData().toString()).doesNotContain("CARD","PRIVATE NAME");
    }
    @ParameterizedTest @ValueSource(strings={"LIBRARIAN","LIBRARY_MANAGER","ADMIN"})
    void staffReadsExistingOverdueServiceWithOwnActor(String role) {
        login(role,13);when(loans.overdueLoans(13L)).thenReturn(List.of());
        assertThat(run("getAuthorizedLoans","{\"overdueOnly\":true}").block().items()).isEmpty();verify(loans).overdueLoans(13L);
    }
    @ParameterizedTest @ValueSource(strings={"LIBRARY_MANAGER","ADMIN"})
    void authorizedStatisticsAreRealServiceAggregates(String role) {
        login(role,13);var start=LocalDate.now().withDayOfMonth(1);var end=LocalDate.now();
        when(reports.report(any(),any())).thenReturn(new LoanReportResponse(start,end,17,12,8,2,25,List.of(),List.of(new LoanReportResponse.DailyLoanCount(start,17)),OffsetDateTime.now(),List.of()));
        when(bookRepo.count()).thenReturn(33L);when(copyRepo.count()).thenReturn(101L);
        var out=run("getAuthorizedStatistics","{}");
        assertThat(out.block().items().get(2)).containsEntry("value",17L);assertThat(out.sensitive()).isTrue();
        assertThat(out.providerData().toString()).doesNotContain("Đầu sách hiện tại", "Lượt mượn bản sao", "daily", "17", "101", "displayed", "total");
    }
    @Test void authorizedOverdueNavigationUsesOnlyValidatedFilters() {
        login("LIBRARIAN",13);
        assertThat(run("openOverdueFiltered","{\"minimumDays\":7}").block().actions().get(0).href())
            .isEqualTo("/loans/overdue?minDays=7&exclusive=1");
        assertThatThrownBy(()->run("openOverdueFiltered","{\"minimumDays\":999}")).isInstanceOf(ApiException.class);
    }
    @Test void publicSearchUsesPagedDomainService() {
        login("PUBLIC",0);when(catalog.searchPublicBooks("Java",null,null,false,0,6,"relevance")).thenReturn(new PublicCatalogPageResponse(List.of(),0,6,0,0,true,true,"relevance"));
        assertThat(run("searchBooks","{\"query\":\"Java\"}").block().meta()).containsEntry("total",0L);
        verify(catalog).searchPublicBooks("Java",null,null,false,0,6,"relevance");
    }
    @Test void navigationCannotEscalateOrCreateUrl() {
        login("READER",11);
        assertThatThrownBy(()->run("navigate","{\"destination\":\"statistics\"}")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->run("navigate","{\"destination\":\"https://evil.invalid\"}")).isInstanceOf(AccessDeniedException.class);
        assertThat(run("navigate","{\"destination\":\"myLoans\"}").block().actions().get(0).href()).isEqualTo("/my-borrowed-books");
    }
    @Test void mismatchedAuthorityCannotReadPrivateTools() {
        login("READER",11);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(access.principal(),null,List.of(new SimpleGrantedAuthority("ROLE_PUBLIC"))));
        assertThatThrownBy(()->run("getMyLoans","{}")).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(loans);
    }
}
