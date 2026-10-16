-- S2-09.4: preserve legacy cancellations and staff-account deletion behavior.
ALTER TABLE book_reservations
    ADD COLUMN cancelled_by BIGINT,
    ADD COLUMN cancelled_by_name VARCHAR(255),
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_reservations_cancelled_by
        FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_reservations_cancellation_audit CHECK (
        cancelled_at IS NULL OR (
            status = 'CANCELLED'
            AND cancelled_by_name IS NOT NULL AND length(btrim(cancelled_by_name)) > 0
            AND cancellation_reason IS NOT NULL AND length(btrim(cancellation_reason)) > 0
        )
    );
