package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionReviewServiceTest {
    final String fingerprint = "a".repeat(64);
    final OffsetDateTime start = OffsetDateTime.now().minusMinutes(1), end = start.plusSeconds(20);
    PermissionReviewCatalog catalog;
    PermissionReviewInspector inspector;
    PermissionReviewRepository repository;
    UserRepository users;
    PermissionReviewService service;
    @BeforeEach void setup() throws Exception {
        catalog = new PermissionReviewCatalog(new ObjectMapper());
        inspector = mock(PermissionReviewInspector.class); repository = mock(PermissionReviewRepository.class); users = mock(UserRepository.class);
        when(inspector.inspect()).thenReturn(new Inspection(catalog.policy().version(), "approved", fingerprint,
                end, catalog.policy().roles(), catalog.policy().modules(), 84, 0, 0, List.of()));
        var role = new Role(); role.setCode("ADMIN"); var actor = new User(); actor.setId(1L);
        actor.setRole(role); actor.setStatus("ACTIVE"); actor.setFullName("Quản trị kiểm thử");
        when(users.findById(1L)).thenReturn(Optional.of(actor));
        when(repository.insert(any(), anyList(), eq(1L), anyString())).thenReturn(9L);
        when(repository.find(9L, fingerprint)).thenReturn(Optional.of(mock(ReportDetail.class)));
        service = new PermissionReviewService(catalog, inspector, repository, users);
    }
    EvidenceCase check(int before, int after, Boolean unchanged, String correction) {
        return new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 403,
                before == 0 ? null : before, after, unchanged, start, end, "Bằng chứng HTTP và đối chiếu dữ liệu thử.", correction);
    }
    ImportReport report(String kind, String hash, List<EvidenceCase> checks) {
        return new ImportReport(1, UUID.randomUUID(), catalog.policy().version(), hash, kind, "MOCKMVC",
                "test source", "Người chạy thử", start, end, checks);
    }
    void rejects(Runnable call, HttpStatus status, String code) {
        ApiException error = assertThrows(ApiException.class, call::run);
        assertEquals(status, error.getStatus()); assertEquals(code, error.getCode());
    }
    @Test void fixedRequiresFailedBeforeSuccessfulRetestAndUnchangedResource() {
        var input = report("SIMULATION", fingerprint, List.of(check(200, 403, true, "Khôi phục cổng quyền đúng trong môi trường mô phỏng.")));
        service.importReport(input, 1L);
        verify(repository).insert(eq(input), argThat(results -> results.get(0).passed()
                && results.get(0).outcome().equals("FIXED")), eq(1L), eq("Quản trị kiểm thử"));
    }
    @Test void forbiddenStatusAloneCannotMarkMutatingEndpointFixed() {
        for (Boolean unchanged : Arrays.asList(false, null)) {
            var input = report("REGRESSION", fingerprint, List.of(check(200, 403, unchanged, "Thêm kiểm tra quyền.")));
            service.importReport(input, 1L);
            verify(repository).insert(eq(input), argThat(results -> !results.get(0).passed()
                    && results.get(0).outcome().equals("OPEN")), eq(1L), anyString());
        }
    }
    @Test void wrongRoleExpectationDuplicateCasesMissingCorrectionAndChangedBuildAreRejected() {
        var forged = new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 200,
                null, 200, null, start, end, "forged", null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(forged)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        var valid = check(0, 403, true, null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(valid, valid)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(check(200, 403, true, ""))), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.importReport(report("REGRESSION", "b".repeat(64), List.of(valid)), 1L), HttpStatus.CONFLICT, "PERMISSION_BUILD_CHANGED");
        verify(repository, never()).insert(any(), anyList(), anyLong(), anyString());
    }
    @Test void inactiveNonAdminUnknownActorAndDuplicateRunCannotImport() {
        var valid = report("REGRESSION", fingerprint, List.of(check(0, 403, true, null)));
        rejects(() -> service.importReport(valid, null), HttpStatus.FORBIDDEN, "FORBIDDEN");
        rejects(() -> service.importReport(valid, 999L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        var actor = users.findById(1L).orElseThrow(); actor.getRole().setCode("LIBRARIAN");
        rejects(() -> service.importReport(valid, 1L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        actor.getRole().setCode("ADMIN"); actor.setStatus("LOCKED");
        rejects(() -> service.importReport(valid, 1L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        actor.setStatus("ACTIVE"); when(repository.insert(any(), anyList(), anyLong(), anyString())).thenThrow(new DuplicateKeyException("duplicate"));
        rejects(() -> service.importReport(valid, 1L), HttpStatus.CONFLICT, "PERMISSION_REPORT_DUPLICATE");
    }
    @Test void importedTimesPaginationAndMissingReportAreValidated() {
        var outside = new EvidenceCase("FeePaymentController.collect", "READER", "STANDARD", 403, null, 403,
                true, start.minusSeconds(1), end, "outside", null);
        rejects(() -> service.importReport(report("REGRESSION", fingerprint, List.of(outside)), 1L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.reports(-1, 20), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.reports(0, 51), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        rejects(() -> service.detail(0L), HttpStatus.BAD_REQUEST, "INVALID_PERMISSION_REPORT");
        when(repository.find(99L, fingerprint)).thenReturn(Optional.empty());
        rejects(() -> service.detail(99L), HttpStatus.NOT_FOUND, "PERMISSION_REPORT_NOT_FOUND");
    }
}
