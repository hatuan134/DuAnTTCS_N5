-- S4-05.2: Bổ sung các nhóm nhắc quá hạn 1, 7, 30 ngày và cột tổng phí tạm tính
ALTER TABLE loan_reminder_logs DROP CONSTRAINT IF EXISTS loan_reminder_logs_reminder_group_check;
ALTER TABLE loan_reminder_logs ADD CONSTRAINT loan_reminder_logs_reminder_group_check
    CHECK (reminder_group IN ('DUE_IN_2_DAYS', 'DUE_TODAY', 'OVERDUE_1_DAY', 'OVERDUE_7_DAYS', 'OVERDUE_30_DAYS'));

ALTER TABLE loan_reminder_logs ADD COLUMN IF NOT EXISTS total_estimated_fee_vnd BIGINT NOT NULL DEFAULT 0;
