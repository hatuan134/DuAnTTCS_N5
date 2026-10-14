package com.duanttcsn5.library.service;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Separate bean ensures @Transactional refresh is invoked through the Spring proxy. */
@Component
public class CardLockSuggestionJob {
    private static final Logger LOG=LoggerFactory.getLogger(CardLockSuggestionJob.class);
    private final CardLockSuggestionService suggestions;
    public CardLockSuggestionJob(CardLockSuggestionService suggestions) { this.suggestions=suggestions; }
    @Scheduled(cron="0 */5 * * * *",zone="Asia/Ho_Chi_Minh")
    public void check() {
        try { suggestions.refresh(); }
        catch(RuntimeException error) { LOG.error("Không cập nhật được đề xuất khóa thẻ; giao dịch đã hoàn tác.",error); }
    }
}
