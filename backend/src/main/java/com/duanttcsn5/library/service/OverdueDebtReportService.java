package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
@PreAuthorize("hasRole('LIBRARY_MANAGER')")
public class OverdueDebtReportService {
    private final OverdueDebtSnapshotService snapshots;
    private final OverdueDebtExportAuditService audit;
    private final OverdueDebtReportRepository repository;
    private final UserRepository users;
    private final OverdueDebtXlsx writer;
    private final Clock clock;
    @Autowired
    public OverdueDebtReportService(OverdueDebtSnapshotService snapshots,OverdueDebtExportAuditService audit,
            OverdueDebtReportRepository repository,UserRepository users,OverdueDebtXlsx writer) {
        this(snapshots,audit,repository,users,writer,Clock.systemUTC());
    }
    OverdueDebtReportService(OverdueDebtSnapshotService snapshots,OverdueDebtExportAuditService audit,
            OverdueDebtReportRepository repository,UserRepository users,OverdueDebtXlsx writer,Clock clock) {
        this.snapshots=snapshots;this.audit=audit;this.repository=repository;this.users=users;this.writer=writer;this.clock=clock;
    }
    public Snapshot preview(Long actor) { manager(actor);return snapshots.snapshot(now()); }
    public Snapshot preview(Long actor,Filter filter) { manager(actor);return snapshots.snapshot(now(),filter); }
    public java.util.List<CardTypeOption> cardTypes(Long actor) { manager(actor);return repository.cardTypes(); }
    public Download export(Long actor) { return export(actor,Filter.ALL); }
    public Download export(Long actor,Filter filter) {
        String name=manager(actor);
        String typeName=filter.cardTypeId()==null?null:repository.cardTypes().stream()
            .filter(t->t.id().equals(filter.cardTypeId())).findFirst()
            .orElseThrow(()->new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CARD_TYPE",
                "Loại thẻ không còn tồn tại. Vui lòng tải lại danh sách loại thẻ.")).name();
        Long logId=filter.equals(Filter.ALL)?audit.start(actor,name,now()):audit.start(actor,name,now(),filter,typeName); // Fail closed: no file without a durable audit entry.
        Snapshot snapshot=null;
        try {
            snapshot=filter.equals(Filter.ALL)?snapshots.snapshot(now()):snapshots.snapshot(now(),filter);
            byte[] content=writer.create(snapshot.readers());
            String date=snapshot.snapshotAt().atZoneSameInstant(OverdueDebtSnapshotService.ZONE)
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            var download=new Download("DanhSachQuaHan_NoPhi_"+date+".xlsx",content);
            audit.finish(logId,"SUCCESS",snapshot.totalReaders(),snapshot.snapshotAt(),now());
            return download;
        } catch(Exception ex) {
            try { audit.finish(logId,"FAILED",snapshot==null?0:snapshot.totalReaders(),snapshot==null?null:snapshot.snapshotAt(),now()); }
            catch(Exception auditFailure) { ex.addSuppressed(auditFailure); }
            org.slf4j.LoggerFactory.getLogger(getClass()).error("S4-08.1 export failed, log id={}",logId,ex);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"OVERDUE_DEBT_EXPORT_FAILED",
                "Không thể hoàn tất xuất Excel. Vui lòng kiểm tra lịch sử và thử lại.");
        }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public History history(Long actor,int page,int size) {
        manager(actor);
        if(page<0 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","Trang phải từ 0, số dòng từ 1 đến 100.");
        return new History(repository.history(size,(long)page*size),page,size,repository.countHistory());
    }
    private String manager(Long id) {
        var user=id==null?null:users.findById(id).orElse(null);
        if(user==null || user.getRole()==null || !"LIBRARY_MANAGER".equals(user.getRole().getCode())
                || !"ACTIVE".equals(user.getStatus()) || (user.getLockedUntil()!=null && user.getLockedUntil().isAfter(now())))
            throw new ApiException(HttpStatus.FORBIDDEN,"LIBRARY_MANAGER_REQUIRED","Chỉ Quản lý thư viện đang hoạt động được xem và xuất danh sách này.");
        return user.getFullName();
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock.withZone(OverdueDebtSnapshotService.ZONE)); }
}
