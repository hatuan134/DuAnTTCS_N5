package com.duanttcsn5.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class LoanReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LoanReminderJob.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LoanReminderService reminderService;

    public LoanReminderJob(LoanReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * S4-05.1: Automatically run daily email reminder check at 7:00 AM Vietnam time.
     * Evaluates open loans due in 2 days or due today.
     */
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDailyReminders() {
        LocalDate today = LocalDate.now(VN_ZONE);
        log.info("Running daily loan reminder scheduled job for date {}", today);
        try {
            var result = reminderService.processDailyReminders(
                    today,
                    "SYSTEM_SCHEDULED",
                    null,
                    "Hệ thống (Lịch 7h sáng)"
            );
            log.info("Scheduled reminder job finished: total={}, sent={}, failed={}, skipped={}",
                    result.totalRecipients(), result.sentCount(), result.failedCount(), result.skippedCount());
        } catch (Exception ex) {
            log.error("Scheduled reminder job encountered an error: {}", ex.getMessage(), ex);
        }
    }
}
