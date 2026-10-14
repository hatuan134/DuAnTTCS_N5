-- S4-07.1: accelerate range-based lending and return reporting.
-- Keep historical records and existing migrations intact.
CREATE INDEX IF NOT EXISTS ix_loan_items_report_borrowed_at
    ON loan_items (borrowed_at);
CREATE INDEX IF NOT EXISTS ix_loan_items_report_returned_at
    ON loan_items (returned_at) WHERE returned_at IS NOT NULL;
