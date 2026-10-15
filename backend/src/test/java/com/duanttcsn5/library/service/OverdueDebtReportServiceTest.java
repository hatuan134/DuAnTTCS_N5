package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverdueDebtReportServiceTest {
    private final OffsetDateTime at=OffsetDateTime.parse("2026-10-15T00:01:00+07:00");
    private final Clock clock=Clock.fixed(at.toInstant(),ZoneOffset.UTC);
    private OverdueDebtSnapshotService snapshots;
    private OverdueDebtExportAuditService audit;
    private OverdueDebtReportRepository repository;
    private UserRepository users;
    private OverdueDebtXlsx writer;
    private OverdueDebtReportService service;
    private Snapshot data;
    @BeforeEach void setup() {
        snapshots=mock(OverdueDebtSnapshotService.class);audit=mock(OverdueDebtExportAuditService.class);
        repository=mock(OverdueDebtReportRepository.class);users=mock(UserRepository.class);writer=mock(OverdueDebtXlsx.class);
        service=new OverdueDebtReportService(snapshots,audit,repository,users,writer,clock);
        var role=new Role();role.setCode("LIBRARY_MANAGER");
        var user=new User();user.setId(12L);user.setFullName("Quản lý A");user.setRole(role);user.setStatus("ACTIVE");
        when(users.findById(12L)).thenReturn(Optional.of(user));
        data=new Snapshot(at,OverdueDebtSnapshotService.FILTER,List.of(new Reader(1L,"001","Nguyễn Văn A","0901",3,15,new BigDecimal("75000"))),1);
        when(snapshots.snapshot(any())).thenReturn(data);
        when(audit.start(eq(12L),eq("Quản lý A"),any())).thenReturn(44L);
    }
    @Test void vietnamFilenameAndSuccessAuditBeforeReturningBytes() throws Exception {
        when(writer.create(data.readers())).thenReturn(new byte[]{1,2,3});
        var result=service.export(12L);
        assertThat(result.filename()).isEqualTo("DanhSachQuaHan_NoPhi_15-10-2026.xlsx");
        assertThat(result.content()).isEqualTo(new byte[]{1,2,3});
        var ordered=inOrder(audit,snapshots,writer);
        ordered.verify(audit).start(eq(12L),eq("Quản lý A"),any());
        ordered.verify(snapshots).snapshot(any());ordered.verify(writer).create(data.readers());
        ordered.verify(audit).finish(eq(44L),eq("SUCCESS"),eq(1),eq(at),any());
    }
    @Test void creationFailureStillWritesFailedAudit() throws Exception {
        when(writer.create(any())).thenThrow(new IOException("test"));
        assertThatThrownBy(()->service.export(12L)).isInstanceOf(ApiException.class);
        verify(audit).finish(eq(44L),eq("FAILED"),eq(1),eq(at),any());
    }
    @Test void snapshotFailureStillWritesFailedAuditWithUnknownCount() {
        when(snapshots.snapshot(any())).thenThrow(new IllegalStateException("calendar"));
        assertThatThrownBy(()->service.export(12L)).isInstanceOf(ApiException.class);
        verify(audit).finish(eq(44L),eq("FAILED"),eq(0),isNull(),any());
        verifyNoInteractions(writer);
    }
    @Test void cannotReturnUnloggedFileIfAuditUnavailable() {
        when(audit.start(any(),any(),any())).thenThrow(new IllegalStateException("database"));
        assertThatThrownBy(()->service.export(12L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(snapshots,writer);
    }
    @Test void failureToFinalizeSuccessDoesNotReturnFile() throws Exception {
        when(writer.create(any())).thenReturn(new byte[]{1});
        doThrow(new IllegalStateException("audit")).when(audit).finish(eq(44L),eq("SUCCESS"),anyInt(),any(),any());
        assertThatThrownBy(()->service.export(12L)).isInstanceOf(ApiException.class);
        verify(audit).finish(eq(44L),eq("FAILED"),eq(1),eq(at),any());
    }
    @Test void revokedOrLockedManagersAndOtherRolesCannotReadExportOrHistory() {
        for(String roleCode:List.of("READER","LIBRARIAN","ADMIN")) {
            users.findById(12L).orElseThrow().getRole().setCode(roleCode);
            assertThatThrownBy(()->service.preview(12L)).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.export(12L)).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.history(12L,0,20)).isInstanceOf(ApiException.class);
        }
        var user=users.findById(12L).orElseThrow();user.getRole().setCode("LIBRARY_MANAGER");user.setLockedUntil(at.plusHours(1));
        assertThatThrownBy(()->service.export(12L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(audit,snapshots,writer);
    }
    @Test void validatesHistoryPageAndSize() {
        assertThatThrownBy(()->service.history(12L,-1,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.history(12L,0,101)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.history(12L,0,0)).isInstanceOf(ApiException.class);
    }
    @Test void zeroReadersStillCreatesAnAuditedWorkbook() throws Exception {
        var empty=new Snapshot(at,OverdueDebtSnapshotService.FILTER,List.of(),0);
        when(snapshots.snapshot(any())).thenReturn(empty);when(writer.create(List.of())).thenReturn(new byte[]{1});
        assertThat(service.export(12L).content()).isNotEmpty();
        verify(audit).finish(eq(44L),eq("SUCCESS"),eq(0),eq(at),any());
    }
}
