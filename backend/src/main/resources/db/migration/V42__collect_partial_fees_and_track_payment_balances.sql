-- S4-02.2: retain the balance at each collection without changing old receipts.
-- V41 receipts are full collections, so their remaining balance is zero.
ALTER TABLE fee_payments
    ADD COLUMN payment_mode VARCHAR(10) NOT NULL DEFAULT 'FULL',
    ADD COLUMN debt_after_vnd BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN debt_before_vnd BIGINT GENERATED ALWAYS AS (total_amount_vnd + debt_after_vnd) STORED,
    ADD CONSTRAINT ck_fee_payment_mode CHECK (payment_mode IN ('FULL', 'PARTIAL')),
    ADD CONSTRAINT ck_fee_payment_balance CHECK (debt_after_vnd >= 0),
    ADD CONSTRAINT ck_full_fee_payment_balance CHECK (payment_mode <> 'FULL' OR debt_after_vnd = 0);

-- V41 has one table-level CHECK with four columns: an item had to settle a fee.
-- Locate that CHECK by its columns rather than depending on PostgreSQL's generated name.
DO $$
DECLARE constraint_name TEXT;
BEGIN
    SELECT conname INTO STRICT constraint_name FROM pg_constraint
    WHERE conrelid = 'fee_payment_items'::regclass AND contype = 'c'
      AND cardinality(conkey) = 4;
    EXECUTE format('ALTER TABLE fee_payment_items DROP CONSTRAINT %I', constraint_name);
END;
$$;
ALTER TABLE fee_payment_items ADD CONSTRAINT ck_fee_payment_item_allocation
    CHECK (previously_paid_vnd + waived_amount_vnd + collected_amount_vnd <= original_amount_vnd);

-- Existing immutable-history and reader/fee locking triggers remain active.
