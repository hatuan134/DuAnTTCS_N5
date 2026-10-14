package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.LoanReportResponse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LoanReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class LoanReportService {
    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LoanReportRepository repository;
    private final Clock clock;

    @Autowired
    public LoanReportService(LoanReportRepository repository) {
        this(repository, Clock.systemUTC());
    }

    // Inject a fixed clock in unit tests to exercise Vietnam midnight and leap-day boundaries.
    LoanReportService(LoanReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LoanReportResponse report(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));
        LocalDate endDate = requestedEnd == null ? today : requestedEnd;
        LocalDate startDate = requestedStart == null ? endDate.minusDays(29) : requestedStart;
        if (startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DATES",
                    "Ngày bắt đầu không được lớn hơn ngày kết thúc.");
        }
        if (endDate.equals(LocalDate.MAX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DATES",
                    "Ngày kết thúc nằm ngoài khoảng ngày được hỗ trợ.");
        }

        var from = startDate.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        var endStart = endDate.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        var untilExclusive = endDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();

        // Queue statistics are an operational snapshot, independent of the loan date range.
        OffsetDateTime queueSnapshotAt = OffsetDateTime.now(clock.withZone(VIETNAM_ZONE));

        var activity = repository.activity(from, untilExclusive);
        var open = repository.openAndOverdue(endStart, untilExclusive);
        double overdueRate = open.open() == 0 ? 0.0
                : Math.round(open.overdue() * 10000.0 / open.open()) / 100.0;
        return new LoanReportResponse(startDate, endDate, activity.borrowed(), activity.returned(),
                open.open(), open.overdue(), overdueRate, repository.topBooks(from, untilExclusive),
                queueSnapshotAt, repository.topQueuedBooks(queueSnapshotAt));
    }
}
