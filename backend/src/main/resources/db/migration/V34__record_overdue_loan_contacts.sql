-- S3-09.4: append-only contact history belongs to the voucher (not each copy).
-- Keep staff id/name as snapshots, consistent with V28/V32 auditing practice.
CREATE TABLE overdue_loan_contacts (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans(id),
    staff_id BIGINT NOT NULL CHECK (staff_id > 0),
    staff_name VARCHAR(150) NOT NULL CHECK (length(btrim(staff_name)) > 0),
    note VARCHAR(1000) NOT NULL CHECK (length(btrim(note)) > 0),
    contacted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_overdue_loan_contacts_latest
    ON overdue_loan_contacts (loan_id, contacted_at DESC, id DESC);
