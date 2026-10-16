-- S4-03.2: Allow ADMIN in addition to LIBRARIAN to adjust incident compensation amount before confirmation.
CREATE OR REPLACE FUNCTION prepare_incident_compensation_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.automatic_compensation_vnd := COALESCE(NEW.automatic_compensation_vnd,round(NEW.cover_price*NEW.factor,0)::bigint);
    IF NEW.compensation_vnd=NEW.automatic_compensation_vnd THEN
        NEW.adjustment_reason := NULL;
    ELSE
        NEW.adjustment_reason := btrim(NEW.adjustment_reason);
        IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
                       WHERE u.id=NEW.actor_user_id AND u.status='ACTIVE' AND r.code IN ('LIBRARIAN', 'ADMIN')) THEN
            RAISE EXCEPTION 'Chỉ Thủ thư hoặc Quản trị viên đang hoạt động được điều chỉnh tiền bồi thường.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
