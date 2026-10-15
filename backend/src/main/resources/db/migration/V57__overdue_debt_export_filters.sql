-- S4-08.2: retain historical labels even if a card type is renamed or removed.
ALTER TABLE overdue_debt_exports
    ADD COLUMN min_overdue_days INTEGER CHECK (min_overdue_days >= 0),
    ADD COLUMN card_type_id BIGINT CHECK (card_type_id > 0),
    ADD COLUMN card_type_name VARCHAR(100),
    ADD CONSTRAINT ck_export_card_type_label CHECK ((card_type_id IS NULL) = (card_type_name IS NULL));

CREATE OR REPLACE FUNCTION protect_overdue_debt_export_log() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN RAISE EXCEPTION 'Không được xóa nhật ký xuất dữ liệu.'; END IF;
    IF OLD.actor_user_id IS NOT NULL AND NEW.actor_user_id IS NULL
        AND (to_jsonb(NEW) - 'actor_user_id') = (to_jsonb(OLD) - 'actor_user_id') THEN
        RETURN NEW;
    END IF;
    IF OLD.status <> 'PROCESSING' OR NEW.status NOT IN ('SUCCESS','FAILED')
        OR (to_jsonb(NEW) - ARRAY['status','record_count','snapshot_at','completed_at'])
            IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['status','record_count','snapshot_at','completed_at']) THEN
        RAISE EXCEPTION 'Không được thay đổi thông tin nhật ký xuất dữ liệu.';
    END IF;
    RETURN NEW;
END;
$$;
