-- S4-09.2: server-run provenance and immutable completion; no business fixtures.
CREATE TABLE permission_review_jobs (
    run_key UUID PRIMARY KEY,
    state VARCHAR(20) NOT NULL CHECK (state IN ('STARTING','RUNNING','SUCCEEDED','FAILED')),
    message VARCHAR(500) NOT NULL,
    requested_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    requested_by_name VARCHAR(255) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    report_id BIGINT UNIQUE REFERENCES permission_review_runs(id),
    CHECK ((state IN ('STARTING','RUNNING') AND finished_at IS NULL)
        OR (state IN ('SUCCEEDED','FAILED') AND finished_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_permission_review_one_active_job ON permission_review_jobs ((true))
    WHERE state IN ('STARTING','RUNNING');
CREATE TABLE permission_review_completions (
    report_id BIGINT PRIMARY KEY REFERENCES permission_review_runs(id),
    confirmed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    confirmed_by_name VARCHAR(255) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fingerprint CHAR(64) NOT NULL,
    role_case_count INTEGER NOT NULL CHECK (role_case_count > 0)
);
CREATE FUNCTION reject_permission_completion_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.confirmed_by_user_id IS NOT NULL AND NEW.confirmed_by_user_id IS NULL
       AND (to_jsonb(NEW) - 'confirmed_by_user_id') = (to_jsonb(OLD) - 'confirmed_by_user_id') THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Permission review completion is immutable';
END;
$$;
CREATE TRIGGER protect_permission_review_completion BEFORE UPDATE OR DELETE ON permission_review_completions
    FOR EACH ROW EXECUTE FUNCTION reject_permission_completion_change();
