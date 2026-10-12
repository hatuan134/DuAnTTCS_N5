-- S4-01.3: preserve the original charge/payment; approved waivers reduce receivables.
ALTER TABLE reader_fees ADD COLUMN waived_amount_vnd BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reader_fees ADD CONSTRAINT ck_reader_fee_waived_amount
    CHECK (waived_amount_vnd >= 0 AND waived_amount_vnd <= amount_vnd - paid_amount_vnd);
DROP INDEX ix_reader_fees_unpaid_reader;
CREATE INDEX ix_reader_fees_unpaid_reader ON reader_fees(reader_user_id)
    WHERE paid_amount_vnd + waived_amount_vnd < amount_vnd;

ALTER TABLE fee_reduction_requests
    ADD COLUMN approved_amount_vnd BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN decided_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN decider_name VARCHAR(150),
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN decision_reason VARCHAR(1000);
ALTER TABLE fee_reduction_requests ADD CONSTRAINT ck_fee_reduction_decision CHECK (
    (status = 'PENDING' AND approved_amount_vnd = 0 AND decided_by IS NULL
        AND decider_name IS NULL AND decided_at IS NULL AND decision_reason IS NULL)
    OR (status = 'APPROVED' AND approved_amount_vnd = requested_amount_vnd
        AND decider_name IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NULL)
    OR (status = 'REJECTED' AND approved_amount_vnd = 0
        AND decider_name IS NOT NULL AND decided_at IS NOT NULL
        AND decision_reason IS NOT NULL AND length(btrim(decision_reason, E' \t\n\r')) > 0)
);
CREATE INDEX ix_fee_reduction_history ON fee_reduction_requests(reader_fee_id, submitted_at DESC, id DESC);

-- Keep new proposals compatible with already approved partial waivers.
CREATE OR REPLACE FUNCTION guard_fee_reduction_request() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE fee_amount BIGINT; fee_paid BIGINT; fee_waived BIGINT; fee_item BIGINT; actor_name TEXT;
BEGIN
    SELECT amount_vnd, paid_amount_vnd, waived_amount_vnd, return_item_id
    INTO fee_amount, fee_paid, fee_waived, fee_item
    FROM reader_fees WHERE id = NEW.reader_fee_id FOR UPDATE;
    IF NOT FOUND OR fee_item IS NULL OR fee_amount - fee_waived <= 0 OR fee_paid <> 0 THEN
        RAISE EXCEPTION 'Chỉ khoản phí trễ hạn còn phải thu và chưa thu tiền mới được đề nghị miễn giảm.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM return_fee_assessments a JOIN loan_items li ON li.id = a.item_id
        WHERE a.reader_fee_id = NEW.reader_fee_id AND a.item_id = fee_item AND li.returned_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Khoản phí chưa có kết quả nhận trả hợp lệ.';
    END IF;
    SELECT u.full_name INTO actor_name FROM users u JOIN roles r ON r.id = u.role_id
    WHERE u.id = NEW.requested_by AND u.status = 'ACTIVE' AND r.code = 'LIBRARIAN';
    IF NOT FOUND THEN RAISE EXCEPTION 'Chỉ Thủ thư được gửi đề nghị miễn giảm.'; END IF;
    IF NEW.original_amount_vnd IS DISTINCT FROM fee_amount OR NEW.status <> 'PENDING'
        OR NEW.requested_amount_vnd > fee_amount - fee_waived THEN
        RAISE EXCEPTION 'Đề nghị phải giữ phí gốc, ở trạng thái Chờ duyệt và không vượt khoản còn phải thu.';
    END IF;
    NEW.requester_name := actor_name;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_fee_reduction_decision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE fee_amount BIGINT; fee_paid BIGINT; fee_waived BIGINT; actor_name TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Không được xóa lịch sử đề nghị miễn giảm.'; END IF;
    -- User deletion can clear FK identities; snapshot names/decisions must survive.
    IF (to_jsonb(NEW) - 'requested_by' - 'decided_by') = (to_jsonb(OLD) - 'requested_by' - 'decided_by')
        AND (NEW.requested_by IS NOT DISTINCT FROM OLD.requested_by OR NEW.requested_by IS NULL)
        AND (NEW.decided_by IS NOT DISTINCT FROM OLD.decided_by OR NEW.decided_by IS NULL) THEN
        RETURN NEW;
    END IF;
    IF OLD.status <> 'PENDING' THEN RAISE EXCEPTION 'Đề nghị đã được xử lý, không được sửa quyết định.'; END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.reader_fee_id IS DISTINCT FROM OLD.reader_fee_id
        OR NEW.requested_by IS DISTINCT FROM OLD.requested_by
        OR NEW.requester_name IS DISTINCT FROM OLD.requester_name
        OR NEW.original_amount_vnd IS DISTINCT FROM OLD.original_amount_vnd
        OR NEW.requested_amount_vnd IS DISTINCT FROM OLD.requested_amount_vnd
        OR NEW.reason IS DISTINCT FROM OLD.reason OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at THEN
        RAISE EXCEPTION 'Không được sửa nội dung đề nghị đã gửi.';
    END IF;
    IF NEW.status NOT IN ('APPROVED', 'REJECTED') THEN RAISE EXCEPTION 'Quyết định không hợp lệ.'; END IF;
    SELECT u.full_name INTO actor_name FROM users u JOIN roles r ON r.id = u.role_id
    WHERE u.id = NEW.decided_by AND u.status = 'ACTIVE' AND r.code = 'LIBRARY_MANAGER';
    IF NOT FOUND THEN RAISE EXCEPTION 'Chỉ Quản lý thư viện được quyết định miễn giảm.'; END IF;
    SELECT amount_vnd, paid_amount_vnd, waived_amount_vnd INTO fee_amount, fee_paid, fee_waived
    FROM reader_fees WHERE id = OLD.reader_fee_id FOR UPDATE;
    IF NOT FOUND OR fee_amount IS DISTINCT FROM OLD.original_amount_vnd THEN
        RAISE EXCEPTION 'Khoản phí không hợp lệ.';
    END IF;
    IF NEW.status = 'APPROVED' THEN
        IF fee_paid <> 0 THEN RAISE EXCEPTION 'Khoản phí đã thu tiền, không được phê duyệt miễn giảm.'; END IF;
        IF OLD.requested_amount_vnd > fee_amount - fee_paid - fee_waived THEN
            RAISE EXCEPTION 'Số tiền miễn giảm vượt khoản còn phải thu.';
        END IF;
        NEW.approved_amount_vnd := OLD.requested_amount_vnd;
        NEW.decision_reason := NULL;
    ELSE
        IF NEW.decision_reason IS NULL OR length(btrim(NEW.decision_reason, E' \t\n\r')) = 0 THEN
            RAISE EXCEPTION 'Vui lòng nhập lý do từ chối.';
        END IF;
        NEW.approved_amount_vnd := 0;
    END IF;
    NEW.decider_name := actor_name;
    NEW.decided_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_fee_reduction_decision BEFORE UPDATE OR DELETE ON fee_reduction_requests
    FOR EACH ROW EXECUTE FUNCTION guard_fee_reduction_decision();

-- The total may only change to the sum of immutable approved decisions.
CREATE FUNCTION guard_reader_fee_waiver() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE approved_total NUMERIC;
BEGIN
    IF NEW.waived_amount_vnd IS DISTINCT FROM OLD.waived_amount_vnd THEN
        SELECT COALESCE(SUM(approved_amount_vnd), 0) INTO approved_total
        FROM fee_reduction_requests WHERE reader_fee_id = OLD.id AND status = 'APPROVED';
        IF NEW.waived_amount_vnd IS DISTINCT FROM approved_total THEN
            RAISE EXCEPTION 'Số tiền miễn giảm phải khớp lịch sử đã phê duyệt.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_reader_fee_waiver BEFORE UPDATE ON reader_fees
    FOR EACH ROW EXECUTE FUNCTION guard_reader_fee_waiver();

CREATE FUNCTION apply_approved_fee_reduction() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE reader_fees SET waived_amount_vnd = waived_amount_vnd + NEW.approved_amount_vnd
    WHERE id = NEW.reader_fee_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_apply_approved_fee_reduction AFTER UPDATE ON fee_reduction_requests
    FOR EACH ROW WHEN (OLD.status = 'PENDING' AND NEW.status = 'APPROVED')
    EXECUTE FUNCTION apply_approved_fee_reduction();
