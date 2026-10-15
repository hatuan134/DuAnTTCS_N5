package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OverdueDebtSnapshotService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String FILTER="Quá hạn HOẶC còn nợ; một dòng/bạn đọc; ngày mở cửa sau hạn trả đến ngày chốt (giờ Việt Nam); tất cả khoản phải thu hợp lệ trừ đã thu/miễn giảm, loại bồi thường đã điều chỉnh hủy.";
    private final OverdueDebtReportRepository repository;
    private final LibraryConfigurationService configuration;
    public OverdueDebtSnapshotService(OverdueDebtReportRepository repository,LibraryConfigurationService configuration) {
        this.repository=repository; this.configuration=configuration;
    }
    // Separate proxied bean: all inputs, including calendar and fees, share one DB snapshot.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot snapshot(OffsetDateTime at) {
        LocalDate today=at.atZoneSameInstant(ZONE).toLocalDate();
        var start=today.atStartOfDay(ZONE).toOffsetDateTime();
        var items=repository.overdueItems(start);
        var dates=items.stream().map(i->i.dueAt().atZoneSameInstant(ZONE).toLocalDate()).collect(Collectors.toSet());
        var days=configuration.calculateOverdueOpenDays(dates,today);
        Map<Long,Long> counts=new HashMap<>(),maxima=new HashMap<>();
        for(var item:items) {
            counts.merge(item.readerId(),1L,Long::sum);
            maxima.merge(item.readerId(),days.getOrDefault(item.dueAt().atZoneSameInstant(ZONE).toLocalDate(),0L),Math::max);
        }
        var readers=repository.readers(start).stream().map(r->new Reader(r.readerId(),r.cardNumber(),r.fullName(),r.phone(),
            counts.getOrDefault(r.readerId(),0L),maxima.getOrDefault(r.readerId(),0L),r.debt())).toList();
        return new Snapshot(at,FILTER,readers,readers.size());
    }
}
