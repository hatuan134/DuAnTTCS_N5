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
    @Test void filteredExportUsesSameRowsAsPreviewAndAuditsBothConditions() throws Exception {
        var filter=new Filter(7,2L);
        when(repository.cardTypes()).thenReturn(List.of(new CardTypeOption(2L,"Sinh viên",false)));
        when(snapshots.snapshot(any(),eq(filter))).thenReturn(data);
        when(audit.start(eq(12L),eq("Quản lý A"),any(),eq(filter),eq("Sinh viên"))).thenReturn(45L);
        when(writer.create(data.readers())).thenReturn(new OverdueDebtXlsx().create(data.readers()));
        var preview=service.preview(12L,filter);
        var file=service.export(12L,filter);
        verify(writer).create(preview.readers());
        verify(audit).start(eq(12L),eq("Quản lý A"),any(),eq(filter),eq("Sinh viên"));
        verify(audit).finish(eq(45L),eq("SUCCESS"),eq(preview.totalReaders()),eq(at),any());
        assertThat(file.filename()).isEqualTo("DanhSachQuaHan_NoPhi_15-10-2026.xlsx");
        String sheet=null;
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(file.content()))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) if(entry.getName().equals("xl/worksheets/sheet1.xml"))
                sheet=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(sheet).isNotNull();
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var xml=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(sheet.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var rows=xml.getElementsByTagName("row");
        assertThat(rows.getLength()).isEqualTo(preview.totalReaders()+1);
        for(int i=0;i<preview.readers().size();i++) {
            var row=preview.readers().get(i);
            var cells=((org.w3c.dom.Element)rows.item(i+1)).getElementsByTagName("c");
            assertThat(cells.getLength()).isEqualTo(6);
            assertThat(cells.item(0).getTextContent()).isEqualTo(row.cardNumber());
            assertThat(cells.item(1).getTextContent()).isEqualTo(row.fullName());
            assertThat(cells.item(2).getTextContent()).isEqualTo(row.phone());
            assertThat(cells.item(3).getTextContent()).isEqualTo(Long.toString(row.overdueBooks()));
            assertThat(cells.item(4).getTextContent()).isEqualTo(Long.toString(row.maxOverdueDays()));
            assertThat(new BigDecimal(cells.item(5).getTextContent())).isEqualByComparingTo(row.outstandingVnd());
        }
    }
    @Test void missingCardTypeCannotFallBackToUnfilteredExport() {
        when(repository.cardTypes()).thenReturn(List.of());
        assertThatThrownBy(()->service.export(12L,new Filter(7,999L))).isInstanceOf(ApiException.class);
        verifyNoInteractions(audit,snapshots,writer);
    }
    @Test void filteredFailureRetainsAuditAndReturnsNoWorkbook() throws Exception {
        var filter=new Filter(0,null);
        when(snapshots.snapshot(any(),eq(filter))).thenReturn(data);
        when(audit.start(eq(12L),eq("Quản lý A"),any(),eq(filter),isNull())).thenReturn(46L);
        when(writer.create(any())).thenThrow(new IOException("test"));
        assertThatThrownBy(()->service.export(12L,filter)).isInstanceOf(ApiException.class);
        verify(audit).finish(eq(46L),eq("FAILED"),eq(1),eq(at),any());
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
