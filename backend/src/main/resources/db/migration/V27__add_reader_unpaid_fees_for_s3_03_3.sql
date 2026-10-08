-- S3-03.3: track the outstanding VND amount without introducing a payment workflow.
-- A fee is settled when paid_amount_vnd = amount_vnd. Partial payments leave the remainder due.
CREATE TABLE reader_fees (
    id BIGSERIAL PRIMARY KEY,
    reader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    description VARCHAR(500),
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    paid_amount_vnd BIGINT NOT NULL DEFAULT 0
        CHECK (paid_amount_vnd >= 0 AND paid_amount_vnd <= amount_vnd),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_reader_fees_unpaid_reader
    ON reader_fees(reader_user_id)
    WHERE paid_amount_vnd < amount_vnd;
