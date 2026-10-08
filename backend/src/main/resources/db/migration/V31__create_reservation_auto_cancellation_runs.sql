-- S3-06.3: Record reservation auto-cancellation runs and prevent double-processing.
CREATE TABLE reservation_auto_cancellation_runs (
    id BIGSERIAL PRIMARY KEY,
    run_date DATE NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_identified INTEGER NOT NULL DEFAULT 0,
    total_cancelled INTEGER NOT NULL DEFAULT 0,
    total_transferred INTEGER NOT NULL DEFAULT 0,
    total_released INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    triggered_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM'
);

CREATE INDEX idx_res_auto_cancel_runs_started_at ON reservation_auto_cancellation_runs (started_at DESC);
CREATE INDEX idx_res_auto_cancel_runs_run_date ON reservation_auto_cancellation_runs (run_date DESC);

-- Link cancelled reservations to their run record to prevent duplicate handling
ALTER TABLE book_reservations
    ADD COLUMN auto_cancellation_run_id BIGINT;

ALTER TABLE book_reservations
    ADD CONSTRAINT fk_book_reservations_auto_cancel_run
    FOREIGN KEY (auto_cancellation_run_id)
    REFERENCES reservation_auto_cancellation_runs (id)
    ON DELETE SET NULL;

CREATE INDEX idx_book_reservations_auto_cancel_run_id ON book_reservations (auto_cancellation_run_id);
