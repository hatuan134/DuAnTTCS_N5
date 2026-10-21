-- Separate delivery ledger: historical logs (including duplicate failures) remain untouched.
CREATE TABLE loan_reminder_claims (
    reminder_date DATE NOT NULL,
    reader_id BIGINT NOT NULL REFERENCES users(id),
    reminder_group VARCHAR(30) NOT NULL,
    log_id BIGINT UNIQUE REFERENCES loan_reminder_logs(id),
    state VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reminder_date, reader_id, reminder_group),
    CHECK (state IN ('PROCESSING','SENT','PENDING_RETRY','FAILED','FAILED_MAX_RETRIES','REVIEW_REQUIRED','SKIPPED'))
);

-- Collapse only the new ledger, never audit rows. Legacy failures cannot prove non-delivery.
INSERT INTO loan_reminder_claims (reminder_date, reader_id, reminder_group, log_id, state)
SELECT DISTINCT ON (reminder_date, reader_id,
    CASE WHEN reminder_group LIKE 'OVERDUE_%' THEN 'OVERDUE_DAILY' ELSE reminder_group END)
    reminder_date, reader_id,
    CASE WHEN reminder_group LIKE 'OVERDUE_%' THEN 'OVERDUE_DAILY' ELSE reminder_group END,
    id, CASE WHEN status = 'SENT' THEN 'SENT' ELSE 'REVIEW_REQUIRED' END
FROM loan_reminder_logs
WHERE is_dry_run = FALSE AND status <> 'SKIPPED'
ORDER BY reminder_date, reader_id,
    CASE WHEN reminder_group LIKE 'OVERDUE_%' THEN 'OVERDUE_DAILY' ELSE reminder_group END,
    (status = 'SENT') DESC, id;

ALTER TABLE loan_reminder_logs DROP CONSTRAINT loan_reminder_logs_status_check;
ALTER TABLE loan_reminder_logs ADD CONSTRAINT loan_reminder_logs_status_check
    CHECK (status IN ('SENT','FAILED','SKIPPED','PENDING_RETRY','FAILED_MAX_RETRIES','PROCESSING','REVIEW_REQUIRED'));
ALTER TABLE loan_reminder_logs DROP CONSTRAINT loan_reminder_logs_reminder_group_check;
ALTER TABLE loan_reminder_logs ADD CONSTRAINT loan_reminder_logs_reminder_group_check
    CHECK (reminder_group IN ('DUE_IN_2_DAYS','DUE_TODAY','OVERDUE_1_DAY','OVERDUE_7_DAYS','OVERDUE_30_DAYS','OVERDUE_DAILY'));
ALTER TABLE loan_reminder_retry_attempts DROP CONSTRAINT loan_reminder_retry_attempts_status_check;
ALTER TABLE loan_reminder_retry_attempts ADD CONSTRAINT loan_reminder_retry_attempts_status_check
    CHECK (status IN ('SUCCESS','FAILED','UNKNOWN','SKIPPED'));
