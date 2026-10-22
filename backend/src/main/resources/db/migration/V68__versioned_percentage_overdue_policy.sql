-- No default monetary rate; an empty policy table leaves legacy loans unchanged.
CREATE TABLE overdue_percentage_policies (
    id BIGSERIAL PRIMARY KEY,
    daily_percent NUMERIC(9,4) NOT NULL CHECK (daily_percent > 0 AND daily_percent <= 100),
    cap_percent NUMERIC(9,4) CHECK (cap_percent > 0 AND cap_percent <= 10000),
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_policy_id BIGINT REFERENCES overdue_percentage_policies(id),
    CHECK (effective_at >= created_at)
);
CREATE INDEX ix_overdue_percentage_effective ON overdue_percentage_policies(effective_at DESC,id DESC);
CREATE TABLE loan_item_overdue_policies (
    item_id BIGINT PRIMARY KEY REFERENCES loan_items(id) ON DELETE RESTRICT,
    policy_id BIGINT NOT NULL REFERENCES overdue_percentage_policies(id) ON DELETE RESTRICT,
    cover_price NUMERIC(12,2) NOT NULL CHECK (cover_price > 0),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER protect_overdue_percentage_policy BEFORE UPDATE OR DELETE ON overdue_percentage_policies
FOR EACH ROW EXECUTE FUNCTION reject_permission_review_change();
CREATE TRIGGER protect_loan_item_overdue_policy BEFORE UPDATE OR DELETE ON loan_item_overdue_policies
FOR EACH ROW EXECUTE FUNCTION reject_permission_review_change();

CREATE FUNCTION capture_overdue_percentage_policy() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy_id_value BIGINT; price_value NUMERIC(12,2);
BEGIN
    -- Serialize activation with handover, using the existing configuration lock.
    PERFORM id FROM library_overdue_fee_settings WHERE id=1 FOR SHARE;
    SELECT id INTO policy_id_value FROM overdue_percentage_policies
      WHERE effective_at <= clock_timestamp() ORDER BY effective_at DESC,id DESC LIMIT 1;
    IF policy_id_value IS NOT NULL THEN
        SELECT cover_price INTO price_value FROM book_copies WHERE id=NEW.book_copy_id;
        IF price_value IS NULL OR price_value <= 0 THEN
            RAISE EXCEPTION 'OVERDUE_COVER_PRICE_REQUIRED: Gia bia phai lon hon 0 truoc khi ban giao';
        END IF;
        INSERT INTO loan_item_overdue_policies(item_id,policy_id,cover_price)
          VALUES(NEW.id,policy_id_value,price_value);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER capture_loan_item_overdue_policy AFTER INSERT ON loan_items
FOR EACH ROW EXECUTE FUNCTION capture_overdue_percentage_policy();

-- Preserve the original validation for every legacy snapshot. Percentage caps
-- can be non-multiples of 1,000 VND, so validate those against their own policy.
ALTER TABLE return_fee_assessments DROP CONSTRAINT return_fee_assessments_snapshot_check;
ALTER TABLE return_fee_assessments ADD CONSTRAINT return_fee_assessments_snapshot_check CHECK (
    snapshot->>'status' = 'READY'
    AND (snapshot->>'overdueDays')::BIGINT >= 0
    AND (snapshot->>'amountVnd')::BIGINT >= 0
    AND ((snapshot->>'policyVersion' = 'S4-01.1-OPEN-DAYS-HALF-UP-V1'
          AND (snapshot->>'amountVnd')::BIGINT % 1000 = 0)
      OR (snapshot->>'policyVersion' = 'PERCENT-OPEN-DAYS-HALF-UP-V1'
          AND (snapshot->>'percentagePolicyId')::BIGINT > 0
          AND (snapshot->>'coverPrice')::NUMERIC > 0
          AND (snapshot->>'dailyPercent')::NUMERIC > 0
          AND (snapshot->>'dailyPercent')::NUMERIC <= 100
          AND ((snapshot->>'capPercent') IS NULL OR
              (snapshot->>'amountVnd')::NUMERIC <= floor((snapshot->>'coverPrice')::NUMERIC * (snapshot->>'capPercent')::NUMERIC / 100))))
);
