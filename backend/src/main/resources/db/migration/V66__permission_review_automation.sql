-- CI provenance and replay protection only; no business data is updated.
CREATE TABLE permission_review_ci_receipts (
    run_key UUID PRIMARY KEY REFERENCES permission_review_jobs(run_key),
    repository VARCHAR(150) NOT NULL,
    workflow_run_id BIGINT NOT NULL CHECK (workflow_run_id > 0),
    run_attempt INTEGER NOT NULL CHECK (run_attempt > 0),
    commit_sha VARCHAR(64) NOT NULL CHECK (commit_sha ~ '^[a-f0-9]{40,64}$'),
    branch VARCHAR(150) NOT NULL,
    event_name VARCHAR(30) NOT NULL CHECK (event_name IN ('push','schedule','workflow_dispatch')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL CHECK (completed_at >= started_at),
    total_tests INTEGER NOT NULL CHECK (total_tests >= 0),
    failed_tests INTEGER NOT NULL CHECK (failed_tests >= 0),
    skipped_tests INTEGER NOT NULL CHECK (skipped_tests >= 0),
    body_sha256 CHAR(64) NOT NULL,
    UNIQUE(repository, workflow_run_id, run_attempt),
    CHECK (failed_tests + skipped_tests <= total_tests)
);
CREATE TRIGGER protect_permission_review_ci_receipts
BEFORE UPDATE OR DELETE ON permission_review_ci_receipts
FOR EACH ROW EXECUTE FUNCTION reject_permission_review_change();
