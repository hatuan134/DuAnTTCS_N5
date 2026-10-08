-- S3-03.5: immutable audit snapshots for successful, single-transaction overrides.
-- Existing rejected attempts remain BLOCKED; a successful override is OVERRIDDEN.
ALTER TABLE loan_rejections
    ADD COLUMN event_type VARCHAR(20) NOT NULL DEFAULT 'BLOCKED'
        CHECK (event_type IN ('BLOCKED', 'OVERRIDDEN')),
    ADD COLUMN override_reason VARCHAR(500),
    ADD COLUMN loan_id BIGINT;
ALTER TABLE loan_rejections ADD CONSTRAINT ck_loan_rejection_override_audit CHECK (
    (event_type = 'BLOCKED' AND override_reason IS NULL AND loan_id IS NULL)
    OR (event_type = 'OVERRIDDEN' AND override_reason IS NOT NULL
        AND LENGTH(TRIM(override_reason)) > 0 AND loan_id IS NOT NULL)
);
CREATE UNIQUE INDEX ux_loan_override_per_loan ON loan_rejections(loan_id) WHERE event_type = 'OVERRIDDEN';
