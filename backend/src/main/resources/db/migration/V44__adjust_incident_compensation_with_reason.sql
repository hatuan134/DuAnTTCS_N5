-- S4-03.2: pre-confirmation adjustment only. Confirmed incidents stay immutable.
ALTER TABLE book_incidents ADD COLUMN automatic_compensation_vnd BIGINT;
ALTER TABLE book_incidents ADD COLUMN adjustment_reason VARCHAR(1900);

-- Backfill only the added snapshot, inside Flyway's PostgreSQL transaction.
ALTER TABLE book_incidents DISABLE TRIGGER trg_incident_immutable;
UPDATE book_incidents SET automatic_compensation_vnd=compensation_vnd;
ALTER TABLE book_incidents ENABLE TRIGGER trg_incident_immutable;
ALTER TABLE book_incidents ALTER COLUMN automatic_compensation_vnd SET NOT NULL;

-- V43's unnamed check used the actual amount as the computed amount.
DO $$
DECLARE old_check RECORD;
BEGIN
    FOR old_check IN SELECT conname FROM pg_constraint
        WHERE conrelid='book_incidents'::regclass AND contype='c'
          AND pg_get_constraintdef(oid) LIKE '%compensation_vnd%'
          AND pg_get_constraintdef(oid) LIKE '%round%'
          AND pg_get_constraintdef(oid) LIKE '%cover_price%'
    LOOP
        EXECUTE format('ALTER TABLE book_incidents DROP CONSTRAINT %I',old_check.conname);
    END LOOP;
END;
$$;
ALTER TABLE book_incidents ADD CONSTRAINT ck_incident_automatic_amount CHECK (
    automatic_compensation_vnd BETWEEN 0 AND 9007199254740991
    AND automatic_compensation_vnd=round(cover_price*factor,0)
);
ALTER TABLE book_incidents ADD CONSTRAINT ck_incident_adjustment CHECK (
    (compensation_vnd=automatic_compensation_vnd AND adjustment_reason IS NULL)
    OR (compensation_vnd<>automatic_compensation_vnd
        AND compensation_vnd BETWEEN 0 AND 10000000 AND adjustment_reason IS NOT NULL
        AND length(regexp_replace(adjustment_reason,'[[:space:]]','','g'))>0)
);

-- Also supports old callers that record the automatic amount without the new field.
CREATE FUNCTION prepare_incident_compensation_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.automatic_compensation_vnd := COALESCE(NEW.automatic_compensation_vnd,round(NEW.cover_price*NEW.factor,0)::bigint);
    IF NEW.compensation_vnd=NEW.automatic_compensation_vnd THEN
        NEW.adjustment_reason := NULL;
    ELSE
        NEW.adjustment_reason := btrim(NEW.adjustment_reason);
        IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
                       WHERE u.id=NEW.actor_user_id AND u.status='ACTIVE' AND r.code='LIBRARIAN') THEN
            RAISE EXCEPTION 'Chỉ Thủ thư đang hoạt động được điều chỉnh tiền bồi thường.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_incident_compensation_snapshot BEFORE INSERT ON book_incidents
    FOR EACH ROW EXECUTE FUNCTION prepare_incident_compensation_snapshot();
-- V43 protects UPDATE/DELETE/TRUNCATE and checks the actual receivable amount at commit.
