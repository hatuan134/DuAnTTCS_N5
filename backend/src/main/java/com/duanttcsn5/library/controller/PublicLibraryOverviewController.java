package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.repository.LibraryWeeklyScheduleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

/** Public aggregate figures only. Never exposes member records, loans or account data. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicLibraryOverviewController {
    private final JdbcTemplate jdbc;
    private final LibraryWeeklyScheduleRepository schedules;

    public PublicLibraryOverviewController(JdbcTemplate jdbc, LibraryWeeklyScheduleRepository schedules) {
        this.jdbc = jdbc;
        this.schedules = schedules;
    }

    public record OpeningDay(int dayOfWeek, boolean open, LocalTime openTime, LocalTime closeTime) {}
    public record LibraryOverview(long totalTitles, long totalCopies, long totalReaders,
                                  long totalLoans, List<OpeningDay> weeklySchedule) {}

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<LibraryOverview> getOverview() {
        long[] totals = jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM books) AS titles,
                   (SELECT COUNT(*) FROM book_copies) AS copies,
                   (SELECT COUNT(*) FROM reader_profiles WHERE registration_status = 'APPROVED') AS readers,
                   (SELECT COUNT(*) FROM loans) AS loans
            """, (rs, index) -> new long[] {
                rs.getLong("titles"), rs.getLong("copies"), rs.getLong("readers"), rs.getLong("loans")
            });
        if (totals == null) throw new IllegalStateException("Unable to read library totals");
        List<OpeningDay> weekly = schedules.findAllByOrderByDayOfWeekAsc().stream()
            .map(day -> new OpeningDay(day.getDayOfWeek(), day.isOpen(), day.getOpenTime(), day.getCloseTime()))
            .toList();
        return ResponseEntity.ok(new LibraryOverview(totals[0], totals[1], totals[2], totals[3], weekly));
    }
}
