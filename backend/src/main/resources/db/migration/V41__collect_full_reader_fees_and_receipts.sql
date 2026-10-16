-- S4-02.1: a receipt is the immutable snapshot of one full collection.
CREATE TABLE fee_payments (
    id BIGSERIAL PRIMARY KEY,
    transaction_number TEXT GENERATED ALWAYS AS ('GD-' || id::text) STORED UNIQUE,
    receipt_number TEXT GENERATED ALWAYS AS ('BL-' || id::text) STORED UNIQUE,
    reader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reader_name VARCHAR(150) NOT NULL,
    card_number VARCHAR(100),
    collected_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    collector_name VARCHAR(150) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    total_amount_vnd BIGINT NOT NULL CHECK (total_amount_vnd > 0),
    request_key UUID NOT NULL,
    debt_token CHAR(64) NOT NULL,
    UNIQUE (reader_user_id, request_key)
);
CREATE INDEX ix_fee_payments_reader ON fee_payments(reader_user_id, collected_at DESC, id DESC);

CREATE TABLE fee_payment_items (
    payment_id BIGINT NOT NULL REFERENCES fee_payments(id) ON DELETE RESTRICT,
    reader_fee_id BIGINT NOT NULL REFERENCES reader_fees(id) ON DELETE RESTRICT,
    fee_type VARCHAR(30) NOT NULL CHECK (fee_type IN ('OVERDUE', 'RECORDED')),
    description VARCHAR(500),
    original_amount_vnd BIGINT NOT NULL CHECK (original_amount_vnd > 0),
    previously_paid_vnd BIGINT NOT NULL CHECK (previously_paid_vnd >= 0),
    waived_amount_vnd BIGINT NOT NULL CHECK (waived_amount_vnd >= 0),
    collected_amount_vnd BIGINT NOT NULL CHECK (collected_amount_vnd > 0),
    PRIMARY KEY (payment_id, reader_fee_id),
    CHECK (previously_paid_vnd + waived_amount_vnd + collected_amount_vnd = original_amount_vnd)
);

-- New debt must serialize with a full collection for the same reader.
-- Existing fee updates (including waiver approval) keep their established fee-row locks.
CREATE FUNCTION lock_reader_for_new_fee() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM id FROM users WHERE id = NEW.reader_user_id FOR NO KEY UPDATE;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_lock_reader_for_new_fee BEFORE INSERT ON reader_fees
    FOR EACH ROW EXECUTE FUNCTION lock_reader_for_new_fee();

CREATE FUNCTION guard_fee_payment_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Account deletion may clear the collector FK, but never the printed name.
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'fee_payments'
        AND to_jsonb(NEW)->>'collected_by' IS NULL
        AND (to_jsonb(NEW) - 'collected_by') = (to_jsonb(OLD) - 'collected_by') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Không được sửa hoặc xóa giao dịch và biên lai đã ghi nhận.';
END;
$$;
CREATE TRIGGER trg_guard_fee_payment_history BEFORE UPDATE OR DELETE ON fee_payments
    FOR EACH ROW EXECUTE FUNCTION guard_fee_payment_history();
CREATE TRIGGER trg_guard_fee_payment_item_history BEFORE UPDATE OR DELETE ON fee_payment_items
    FOR EACH ROW EXECUTE FUNCTION guard_fee_payment_history();
