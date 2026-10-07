-- S3-02.5: preserve legacy/reservation loans and identify each direct confirmation.
ALTER TABLE loans
    ADD COLUMN direct_request_id UUID,
    ADD COLUMN direct_request_fingerprint VARCHAR(64),
    ADD CONSTRAINT uq_loans_direct_request UNIQUE (direct_request_id),
    ADD CONSTRAINT ck_loans_direct_request CHECK (
        (direct_request_id IS NULL AND direct_request_fingerprint IS NULL)
        OR (direct_request_id IS NOT NULL AND direct_request_fingerprint IS NOT NULL
            AND length(direct_request_fingerprint) = 64 AND reservation_id IS NULL)
    );
