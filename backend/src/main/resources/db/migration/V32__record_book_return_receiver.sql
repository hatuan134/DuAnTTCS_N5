-- S3-07.2: no invented receiver for historical returns.
-- Receiver id/name are immutable snapshots, following loan_rejections (V28).
-- No user FK: deleting a staff account must not erase history or become blocked (V14).
-- V24's existing return trigger continues to set AVAILABLE in the same transaction.
ALTER TABLE loan_items
    ADD COLUMN returned_by BIGINT,
    ADD COLUMN returned_by_name VARCHAR(255),
    ADD CONSTRAINT ck_loan_item_return_receiver CHECK (
        (returned_by IS NULL AND returned_by_name IS NULL)
        OR (returned_at IS NOT NULL AND returned_by IS NOT NULL AND returned_by > 0
            AND returned_by_name IS NOT NULL AND length(btrim(returned_by_name)) > 0)
    );
