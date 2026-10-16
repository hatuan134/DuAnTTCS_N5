package com.duanttcsn5.library.service;

import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Service
public class OverdueDebtExportAuditService {
    private final OverdueDebtReportRepository repository;
    public OverdueDebtExportAuditService(OverdueDebtReportRepository repository) { this.repository=repository; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long start(Long actor,String name,OffsetDateTime time) {
        return repository.start(actor,name,time,OverdueDebtSnapshotService.FILTER);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long start(Long actor,String name,OffsetDateTime time,
            com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.Filter filter,String cardTypeName) {
        return repository.start(actor,name,time,OverdueDebtSnapshotService.FILTER+" "+filter.describe(cardTypeName),filter,cardTypeName);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(Long id,String status,int count,OffsetDateTime snapshot,OffsetDateTime completed) {
        repository.finish(id,status,count,snapshot,completed);
    }
}
