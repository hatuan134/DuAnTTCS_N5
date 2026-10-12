package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.dto.libraryconfig.OverdueFeeSettingResponse;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.WeeklyDay;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment.ClosedDay;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fee storage extends the existing JDBC lending and reader_fees domain. */
@Repository
public class OverdueFeeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OverdueFeeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }
    public OverdueFeeSettingResponse settings() {
        return jdbc.queryForObject("SELECT daily_rate_vnd, updated_at, updated_by FROM library_overdue_fee_settings WHERE id = 1",
                (rs, i) -> new OverdueFeeSettingResponse(rs.getObject("daily_rate_vnd", Long.class),
                        rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("updated_by", Long.class)));
    }
    /** SHARE blocks price updates until the enclosing return transaction commits.
     * This lock must be obtained before sampling the actual return timestamp. */
    public Long lockRate() {
        return jdbc.queryForObject("SELECT daily_rate_vnd FROM library_overdue_fee_settings WHERE id = 1 FOR SHARE",
                (rs, i) -> rs.getObject("daily_rate_vnd", Long.class));
    }
    public int updateRate(long rate, Long actor) {
        return jdbc.update("""
                UPDATE library_overdue_fee_settings SET daily_rate_vnd = ?, updated_at = clock_timestamp(), updated_by = ?
                WHERE id = 1 AND EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
                    WHERE u.id = ? AND u.status = 'ACTIVE' AND r.code IN ('LIBRARY_MANAGER', 'ADMIN'))
                """, rate, actor, actor);
    }
    public record Calendar(List<WeeklyDay> weekly, List<ClosedDay> closures) {}
    private record CalendarRow(String type, int weekday, boolean open, LocalTime start, LocalTime end,
                               LocalDate date, String reason) {}
    /** One statement reads weekly opening and explicit closures from one MVCC snapshot. */
    public Calendar calendar(LocalDate first, LocalDate last) {
        var rows = jdbc.query("""
                SELECT 'W' AS type, day_of_week, is_open, open_time, close_time,
                       NULL::date AS closed_date, NULL::text AS reason
                FROM library_weekly_schedule
                UNION ALL
                SELECT 'C', NULL::integer, NULL::boolean, NULL::time, NULL::time, closed_date, reason
                FROM library_closed_dates WHERE closed_date BETWEEN ? AND ?
                ORDER BY type, day_of_week, closed_date
                """, (rs, i) -> new CalendarRow(rs.getString("type"), rs.getInt("day_of_week"),
                rs.getBoolean("is_open"), rs.getObject("open_time", LocalTime.class),
                rs.getObject("close_time", LocalTime.class), rs.getObject("closed_date", LocalDate.class),
                rs.getString("reason")), first, last);
        var weekly = new ArrayList<WeeklyDay>(); var closures = new ArrayList<ClosedDay>();
        for (var row : rows) {
            if ("W".equals(row.type())) weekly.add(new WeeklyDay(row.weekday(), row.open(), row.start(), row.end()));
            else closures.add(new ClosedDay(row.date(), row.reason()));
        }
        return new Calendar(List.copyOf(weekly), List.copyOf(closures));
    }
    public ReturnFeeAssessment record(Long itemId, Long readerId, ReturnFeeAssessment assessment) {
        Long feeId = null;
        if (assessment.amountVnd() > 0) {
            feeId = jdbc.queryForObject("""
                    INSERT INTO reader_fees(reader_user_id, description, amount_vnd, return_item_id)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """, Long.class, readerId, "Phí trễ hạn nhận trả, chi tiết phiếu #" + itemId,
                    assessment.amountVnd(), itemId);
        }
        var saved = assessment.withFeeId(feeId);
        jdbc.update("INSERT INTO return_fee_assessments(item_id, reader_fee_id, snapshot) VALUES (?, ?, CAST(? AS jsonb))",
                itemId, feeId, mapper.writeValueAsString(saved));
        return saved;
    }
    /** Read saved JSON only; never recalculate from the current price/calendar. */
    public Map<Long, ReturnFeeAssessment> findForLoan(Long loanId) {
        var result = new HashMap<Long, ReturnFeeAssessment>();
        jdbc.query("""
                SELECT a.item_id, a.snapshot::text FROM return_fee_assessments a
                JOIN loan_items li ON li.id = a.item_id WHERE li.loan_id = ?
                """, rs -> { result.put(rs.getLong("item_id"),
                    mapper.readValue(rs.getString("snapshot"), ReturnFeeAssessment.class)); }, loanId);
        return result;
    }
}
