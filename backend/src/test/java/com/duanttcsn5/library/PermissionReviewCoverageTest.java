package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionReviewCoverageTest {
    PermissionReviewCatalog catalog;
    PermissionReviewService reviews;
    PermissionReviewRepository repository;
    UserRepository users;
    PermissionReviewCoverageService service;
    Inspection inspection;ReportDetail detail;
    final String hash="c".repeat(64);final OffsetDateTime at=OffsetDateTime.now();
    @BeforeEach void setup() throws Exception {
        catalog=new PermissionReviewCatalog(new ObjectMapper());reviews=mock(PermissionReviewService.class);
        repository=mock(PermissionReviewRepository.class);users=mock(UserRepository.class);
        service=new PermissionReviewCoverageService(reviews,repository,users);
        List<ReviewedEntry> entries=catalog.policy().entries().stream().map(e->new ReviewedEntry(e,"documented gate",
                catalog.policy().roles().stream().map(r->new RoleComparison(r.code(),e.rules().get(r.code()),"DENY".equals(e.rules().get(r.code()))?"DENY":"ALLOW",true)).toList(),true,false)).toList();
        inspection=new Inspection(catalog.policy().version(),catalog.policy().decision(),hash,at,catalog.policy().roles(),catalog.policy().modules(),entries.size(),0,0,entries,
                entries.stream().mapToInt(e->e.policy().httpMethods().size()).sum(),List.of());
        when(reviews.inspect()).thenReturn(inspection);when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(true);
        var actor=new User();actor.setId(2L);actor.setFullName("Người xác nhận");actor.setStatus("ACTIVE");var role=new Role();role.setCode("ADMIN");actor.setRole(role);when(users.findById(2L)).thenReturn(Optional.of(actor));
        full();
    }
    void full() {
        List<CaseResult> cases=new ArrayList<>();
        for(var e:catalog.policy().entries())for(String method:e.httpMethods())for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")) {
            String scenario="STANDARD";
            cases.add(result(e,method,role,scenario,true));
            if("OVERRIDE".equals(e.rules().get(role)))cases.add(result(e,method,role,"OVERRIDE",true));
            if("OWN".equals(e.rules().get(role)))cases.add(result(e,method,role,Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(e.id())?"OTHER_OWNER":"SPOOFED_OWNER",true));
        }
        set(cases,true,2,"REGRESSION");
    }
    CaseResult result(PolicyEntry e,String method,String role,String scenario,boolean passed) {
        int status=catalog.requiredStatus(e,role,scenario,200);
        var test=new EvidenceCase(e.id(),role,scenario,status,null,passed?status:500,true,at,at,"Test proof",null,method,passed);
        return new CaseResult(test,e.module(),e.label(),role.equals("ANONYMOUS")?"LOGIN_REQUIRED":e.rules().get(role),passed?"PASS":"OPEN",passed);
    }
    void set(List<CaseResult> cases,boolean current,int schema,String kind) {
        var report=new ImportReport(schema,UUID.randomUUID(),catalog.policy().version(),hash,kind,"ISOLATED_POSTGRES","unit fixture","Tester",at,at,cases.stream().map(CaseResult::test).toList());
        var summary=new ReportSummary(1L,report.runKey(),kind,"ISOLATED_POSTGRES","unit fixture","Tester","Admin",at,at,cases.size(),0,0,(int)cases.stream().filter(c->!c.passed()).count());
        detail=new ReportDetail(summary,report,cases,current);when(reviews.detail(1L)).thenReturn(detail);
    }
    @Test void fullCoverageIncludesBothPutAndPatchAndAllowsAnActiveAdminToComplete() {
        var coverage=service.coverage(1L);assertTrue(coverage.eligible());assertEquals(192,coverage.totalApis());assertEquals(768,coverage.requiredRoleCases());assertEquals(768,coverage.coveredRoleCases());assertEquals(192,coverage.coveredApis());
        service.complete(1L,2L);verify(repository).complete(1L,2L,"Người xác nhận",hash,768);
    }
    @Test void aMissingRoleOrMissingHttpMethodBlocksCompletion() {
        var cases=new ArrayList<>(detail.results());
        cases.removeIf(c->c.test().endpointId().equals("BookCopyController.update") && c.test().httpMethod().equals("PATCH") && c.test().role().equals("READER"));set(cases,true,2,"REGRESSION");
        var coverage=service.coverage(1L);assertFalse(coverage.eligible());assertEquals(767,coverage.coveredRoleCases());
        assertTrue(coverage.missing().stream().anyMatch(m->m.httpMethod().equals("PATCH") && m.role().equals("READER")));
        assertThrows(ApiException.class,()->service.complete(1L,2L));verify(repository,never()).complete(any(),any(),any(),any(),anyInt());
    }
    @Test void missingAnonymousAndMissingOwnershipEvidenceBlockCompletion() {
        var cases=detail.results().stream().filter(c->!c.test().role().equals("ANONYMOUS") && c.test().scenario().equals("STANDARD")).toList();set(cases,true,2,"REGRESSION");
        assertFalse(service.coverage(1L).eligible());assertTrue(service.coverage(1L).missing().stream().anyMatch(m->m.role().equals("ANONYMOUS")));
    }
    @Test void failedCaseStaleSourceExternalImportAndSimulationCannotBeConfirmed() {
        var cases=new ArrayList<>(detail.results());var first=cases.get(0);var e=catalog.find(first.test().endpointId()).orElseThrow();cases.set(0,result(e,first.test().httpMethod(),first.test().role(),first.test().scenario(),false));set(cases,true,2,"REGRESSION");
        assertEquals(1,service.coverage(1L).failedCases());assertFalse(service.coverage(1L).eligible());
        full();set(detail.results(),false,2,"REGRESSION");assertFalse(service.coverage(1L).eligible());
        full();when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(false);assertFalse(service.coverage(1L).eligible());
        when(repository.serverExecuted(eq(1L),any(),eq(hash))).thenReturn(true);set(detail.results(),true,2,"SIMULATION");assertFalse(service.coverage(1L).eligible());
    }
    @Test void undocumentedApiOrMatrixDiscrepancyBlocksEvenOtherwiseCompleteEvidence() {
        when(reviews.inspect()).thenReturn(new Inspection(inspection.policyVersion(),inspection.decision(),hash,at,inspection.roles(),inspection.modules(),190,1,1,inspection.entries(),191,List.of("GET /api/v1/new-api")));
        assertFalse(service.coverage(1L).eligible());assertEquals(1,service.coverage(1L).unknownFunctions());
    }
    @Test void inactiveOrNonAdminActorCannotConfirmAndConfirmationIsIdempotent() {
        assertThrows(ApiException.class,()->service.complete(1L,null));users.findById(2L).orElseThrow().setStatus("LOCKED");assertThrows(ApiException.class,()->service.complete(1L,2L));
        users.findById(2L).orElseThrow().setStatus("ACTIVE");when(repository.completion(1L)).thenReturn(Optional.of(new Completion(1L,"Admin",at,hash)));service.complete(1L,2L);verify(repository,never()).complete(any(),any(),any(),any(),anyInt());
    }
    @Test void databaseGuardRequiresExplicitDifferentLiveDatabaseAndUniqueLoopbackTestName() {
        String name="s4092_review_"+"a".repeat(32),url="jdbc:postgresql://127.0.0.1:5433/"+name;
        assertDoesNotThrow(()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,"library_management"));
        assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,null));
        assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(url,name));
        for(String invalid:List.of("jdbc:postgresql://remote.example:5433/"+name,"jdbc:postgresql://127.0.0.1:5432/"+name,"jdbc:postgresql://127.0.0.1:5433/library_management","jdbc:postgresql://127.0.0.1:5433/s4092_any_name","jdbc:h2:mem:test"))
            assertThrows(IllegalStateException.class,()->FullPermissionDatabaseTest.DatabaseGuard.validate(invalid,"library_management"));
    }

}
