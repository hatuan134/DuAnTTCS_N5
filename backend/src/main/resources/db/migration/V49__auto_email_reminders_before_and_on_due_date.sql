-- S4-05.1: Bảng ghi nhận lịch sử gửi email nhắc nhở trả sách trước hạn và đúng hạn
CREATE TABLE loan_reminder_logs (
    id BIGSERIAL PRIMARY KEY,
    reminder_date DATE NOT NULL,
    reader_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reader_name VARCHAR(255) NOT NULL,
    recipient_email VARCHAR(255),
    reminder_group VARCHAR(30) NOT NULL CHECK (reminder_group IN ('DUE_IN_2_DAYS', 'DUE_TODAY')),
    loan_item_count INT NOT NULL CHECK (loan_item_count > 0),
    loan_numbers TEXT NOT NULL,
    book_titles TEXT NOT NULL,
    items_detail JSONB NOT NULL DEFAULT '[]',
    subject VARCHAR(500) NOT NULL,
    email_content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    error_message VARCHAR(1000),
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM_SCHEDULED',
    triggered_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    triggered_by_user_name VARCHAR(255),
    is_dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_loan_reminder_logs_date ON loan_reminder_logs(reminder_date DESC, sent_at DESC);
CREATE INDEX ix_loan_reminder_logs_reader ON loan_reminder_logs(reader_id);
CREATE INDEX ix_loan_reminder_logs_status ON loan_reminder_logs(status);

-- Ngăn gửi trùng email cho cùng bạn đọc, cùng nhóm nhắc trong cùng ngày chạy
CREATE UNIQUE INDEX uq_loan_reminder_daily ON loan_reminder_logs(reminder_date, reader_id, reminder_group)
    WHERE is_dry_run = FALSE AND status = 'SENT';
