-- S4-05.3: Bổ sung cơ chế tự động gửi lại email thất bại, mã thẻ bạn đọc và nhật ký đối soát chi tiết
ALTER TABLE loan_reminder_logs ADD COLUMN IF NOT EXISTS reader_card_number VARCHAR(100);
ALTER TABLE loan_reminder_logs ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE loan_reminder_logs ADD COLUMN IF NOT EXISTS max_retries INT NOT NULL DEFAULT 3;

ALTER TABLE loan_reminder_logs DROP CONSTRAINT IF EXISTS loan_reminder_logs_status_check;
ALTER TABLE loan_reminder_logs ADD CONSTRAINT loan_reminder_logs_status_check
    CHECK (status IN ('SENT', 'FAILED', 'SKIPPED', 'PENDING_RETRY', 'FAILED_MAX_RETRIES'));

-- Bảng ghi nhận nhật ký chi tiết từng lần gửi và các lần thử lại để phục vụ đối soát
CREATE TABLE IF NOT EXISTS loan_reminder_retry_attempts (
    id BIGSERIAL PRIMARY KEY,
    reminder_log_id BIGINT NOT NULL REFERENCES loan_reminder_logs(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL, -- 1: lần gửi đầu, 2: lần thử lại 1, 3: lần thử lại 2, 4: lần thử lại 3
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    error_message VARCHAR(1000),
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM_SCHEDULED'
);

CREATE INDEX IF NOT EXISTS ix_loan_reminder_retry_log ON loan_reminder_retry_attempts(reminder_log_id, attempt_number);
CREATE INDEX IF NOT EXISTS ix_loan_reminder_logs_card ON loan_reminder_logs(reader_card_number);
