-- S4-09: Cho ADMIN thuc hien cac nghiep vu mien/giam phi da duoc cap quyen tren API.
-- Chi cap nhat phan kiem tra vai tro cua hai trigger function; giu nguyen
-- rang buoc ve khoan phi, nguoi thuc hien ACTIVE, trang thai, so tien va nhat ky.
-- V38/V39 da duoc Flyway ap dung nen khong sua migration cu.
-- CREATE OR REPLACE giu nguyen trigger hien co, khong xoa hoac tao lai du lieu.

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
    WHERE u.id = NEW.requested_by AND u.status = 'ACTIVE' AND r.code IN ('LIBRARIAN', 'ADMIN');
    IF NOT FOUND THEN RAISE EXCEPTION 'Chỉ Thủ thư hoặc Quản trị hệ thống được gửi đề nghị miễn giảm.'; END IF;
    IF NEW.original_amount_vnd IS DISTINCT FROM fee_amount OR NEW.status <> 'PENDING'
        OR NEW.requested_amount_vnd > fee_amount - fee_waived THEN
        RAISE EXCEPTION 'Đề nghị phải giữ phí gốc, ở trạng thái Chờ duyệt và không vượt khoản còn phải thu.';
    END IF;
    NEW.requester_name := actor_name;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION guard_fee_reduction_decision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
    WHERE u.id = NEW.decided_by AND u.status = 'ACTIVE' AND r.code IN ('LIBRARY_MANAGER', 'ADMIN');
    IF NOT FOUND THEN RAISE EXCEPTION 'Chỉ Quản lý thư viện hoặc Quản trị hệ thống được quyết định miễn giảm.'; END IF;
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
