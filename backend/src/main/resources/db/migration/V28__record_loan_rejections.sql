-- S3-03.4: immutable, independent snapshots of refused lending attempts.
-- No foreign keys: a rejection must survive user/card deletion and be writable from
-- REQUIRES_NEW while the loan confirmation transaction holds reader/card locks.
-- PO decision pending: retention time and authorized viewer roles. No auto-deletion.
CREATE TABLE loan_rejections (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    source VARCHAR(30) NOT NULL CHECK (source IN ('CARD_CHECK', 'DIRECT_CONFIRM', 'RESERVATION_CONFIRM')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    reader_user_id BIGINT NOT NULL,
    reader_name VARCHAR(150) NOT NULL,
    card_number VARCHAR(100) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_name VARCHAR(150) NOT NULL,
    reservation_id BIGINT,
    borrowed_books BIGINT NOT NULL CHECK (borrowed_books >= 0),
    max_books INTEGER NOT NULL,
    overdue_loans BIGINT NOT NULL CHECK (overdue_loans >= 0),
    unpaid_amount_vnd NUMERIC(18, 0) NOT NULL CHECK (unpaid_amount_vnd >= 0)
);

CREATE TABLE loan_rejection_reasons (
    id BIGSERIAL PRIMARY KEY,
    rejection_id BIGINT NOT NULL REFERENCES loan_rejections(id) ON DELETE RESTRICT,
    reason_code VARCHAR(80) NOT NULL,
    reason_message TEXT NOT NULL,
    UNIQUE (rejection_id, reason_code)
);

CREATE INDEX ix_loan_rejections_occurred ON loan_rejections(occurred_at DESC, id DESC);
CREATE INDEX ix_loan_rejections_card ON loan_rejections(card_number, occurred_at DESC);
CREATE INDEX ix_loan_rejection_reasons_parent ON loan_rejection_reasons(rejection_id);
