-- S4-08.1: no personal reader data or generated file bytes are retained in the log.
CREATE TABLE overdue_debt_exports (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_name VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    snapshot_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    default_filter TEXT NOT NULL,
    record_count INTEGER NOT NULL DEFAULT 0 CHECK (record_count >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING','SUCCESS','FAILED')),
    CHECK ((status = 'PROCESSING' AND completed_at IS NULL) OR (status <> 'PROCESSING' AND completed_at IS NOT NULL))
);
CREATE INDEX ix_overdue_debt_exports_time ON overdue_debt_exports(requested_at DESC, id DESC);
-- Keep the audit trail. Only the initial PROCESSING -> terminal transition is allowed.
CREATE FUNCTION protect_overdue_debt_export_log() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN RAISE EXCEPTION 'Không được xóa nhật ký xuất dữ liệu.'; END IF;
    -- Preserve the event when an account is deleted; only its FK may be cleared.
    IF OLD.actor_user_id IS NOT NULL AND NEW.actor_user_id IS NULL
        AND (to_jsonb(NEW) - 'actor_user_id') = (to_jsonb(OLD) - 'actor_user_id') THEN
        RETURN NEW;
    END IF;
    IF OLD.status <> 'PROCESSING' OR NEW.status NOT IN ('SUCCESS','FAILED')
        OR NEW.id IS DISTINCT FROM OLD.id OR NEW.actor_user_id IS DISTINCT FROM OLD.actor_user_id
        OR NEW.actor_name IS DISTINCT FROM OLD.actor_name OR NEW.requested_at IS DISTINCT FROM OLD.requested_at
        OR NEW.default_filter IS DISTINCT FROM OLD.default_filter THEN
        RAISE EXCEPTION 'Không được thay đổi thông tin nhật ký xuất dữ liệu.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_protect_overdue_debt_export_log BEFORE UPDATE OR DELETE ON overdue_debt_exports
FOR EACH ROW EXECUTE FUNCTION protect_overdue_debt_export_log();
CREATE TRIGGER trg_no_truncate_overdue_debt_exports BEFORE TRUNCATE ON overdue_debt_exports
FOR EACH STATEMENT EXECUTE FUNCTION protect_overdue_debt_export_log();
