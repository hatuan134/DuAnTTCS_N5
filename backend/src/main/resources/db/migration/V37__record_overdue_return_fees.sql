-- S4-01.1: no invented price or retroactive charge for historical returns.
CREATE TABLE library_overdue_fee_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    daily_rate_vnd BIGINT CHECK (daily_rate_vnd BETWEEN 0 AND 1000000000),
    updated_at TIMESTAMPTZ,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
INSERT INTO library_overdue_fee_settings(id) VALUES (1);

ALTER TABLE reader_fees ADD COLUMN return_item_id BIGINT UNIQUE
    REFERENCES loan_items(id) ON DELETE RESTRICT;

CREATE TABLE return_fee_assessments (
    item_id BIGINT PRIMARY KEY REFERENCES loan_items(id) ON DELETE RESTRICT,
    reader_fee_id BIGINT UNIQUE REFERENCES reader_fees(id) ON DELETE RESTRICT,
    snapshot JSONB NOT NULL CHECK (
        snapshot->>'status' = 'READY'
        AND snapshot->>'policyVersion' = 'S4-01.1-OPEN-DAYS-HALF-UP-V1'
        AND (snapshot->>'overdueDays')::BIGINT >= 0
        AND (snapshot->>'amountVnd')::BIGINT >= 0
        AND (snapshot->>'amountVnd')::BIGINT % 1000 = 0
    ),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (((snapshot->>'amountVnd')::BIGINT = 0 AND reader_fee_id IS NULL)
        OR ((snapshot->>'amountVnd')::BIGINT > 0 AND reader_fee_id IS NOT NULL))
);

-- A calculation is final. Future payment flows may update paid_amount_vnd only.
CREATE FUNCTION guard_return_fee_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Không được sửa hoặc xóa kết quả phí trễ hạn đã ghi nhận.';
END;
$$;
CREATE TRIGGER trg_return_fee_history BEFORE UPDATE OR DELETE ON return_fee_assessments
    FOR EACH ROW EXECUTE FUNCTION guard_return_fee_history();

CREATE FUNCTION guard_reader_return_fee_amount() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.return_item_id IS NOT NULL THEN
            RAISE EXCEPTION 'Không được xóa khoản phí trễ hạn đã ghi nhận.';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.return_item_id IS NOT NULL AND (
        NEW.amount_vnd IS DISTINCT FROM OLD.amount_vnd
        OR NEW.reader_user_id IS DISTINCT FROM OLD.reader_user_id
        OR NEW.return_item_id IS DISTINCT FROM OLD.return_item_id) THEN
        RAISE EXCEPTION 'Không được tính lại khoản phí trễ hạn đã ghi nhận.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_reader_return_fee_amount BEFORE UPDATE OR DELETE ON reader_fees
    FOR EACH ROW EXECUTE FUNCTION guard_reader_return_fee_amount();
