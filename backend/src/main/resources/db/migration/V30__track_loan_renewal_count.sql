-- S3-05.3: One renewal counter per loan (shared by all of its loan items).
-- Existing loans start at zero; previously applied migrations remain untouched.
ALTER TABLE loans
    ADD COLUMN renewal_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE loans
    ADD CONSTRAINT ck_loans_renewal_count_nonnegative CHECK (renewal_count >= 0);
