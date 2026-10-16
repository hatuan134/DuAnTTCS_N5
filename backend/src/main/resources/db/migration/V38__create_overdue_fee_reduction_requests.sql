-- S4-01.2: requests only. No approval, rejection or change to receivables.
CREATE TABLE fee_reduction_requests (
    id BIGSERIAL PRIMARY KEY,
    reader_fee_id BIGINT NOT NULL REFERENCES reader_fees(id) ON DELETE RESTRICT,
    requested_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    requester_name VARCHAR(150) NOT NULL,
    original_amount_vnd BIGINT NOT NULL CHECK (original_amount_vnd > 0),
    requested_amount_vnd BIGINT NOT NULL
        CHECK (requested_amount_vnd > 0 AND requested_amount_vnd <= original_amount_vnd),
    reason VARCHAR(1000) NOT NULL CHECK (length(btrim(reason, E' \t\n\r')) > 0),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- A later slice can process a request; this slice only inserts PENDING rows.
CREATE UNIQUE INDEX ux_fee_reduction_pending ON fee_reduction_requests(reader_fee_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_fee_reduction_pending_time ON fee_reduction_requests(submitted_at DESC, id DESC)
    WHERE status = 'PENDING';

-- Serialize with fee/payment writes and protect the amount and requester snapshots.
CREATE FUNCTION guard_fee_reduction_request() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE fee_amount BIGINT; fee_paid BIGINT; fee_item BIGINT; actor_name TEXT;
BEGIN
    SELECT amount_vnd, paid_amount_vnd, return_item_id INTO fee_amount, fee_paid, fee_item
    FROM reader_fees WHERE id = NEW.reader_fee_id FOR UPDATE;
    IF NOT FOUND OR fee_item IS NULL OR fee_amount <= 0 OR fee_paid <> 0 THEN
        RAISE EXCEPTION 'Chỉ khoản phí trễ hạn đã ghi nhận và chưa thu tiền mới được đề nghị miễn giảm.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM return_fee_assessments a JOIN loan_items li ON li.id = a.item_id
        WHERE a.reader_fee_id = NEW.reader_fee_id AND a.item_id = fee_item AND li.returned_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Khoản phí chưa có kết quả nhận trả hợp lệ.';
    END IF;
    SELECT u.full_name INTO actor_name FROM users u JOIN roles r ON r.id = u.role_id
    WHERE u.id = NEW.requested_by AND u.status = 'ACTIVE' AND r.code = 'LIBRARIAN';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Chỉ Thủ thư được gửi đề nghị miễn giảm.';
    END IF;
    IF NEW.original_amount_vnd IS DISTINCT FROM fee_amount OR NEW.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Đề nghị phải dùng số tiền phí gốc và trạng thái Chờ duyệt.';
    END IF;
    NEW.requester_name := actor_name;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_fee_reduction_request BEFORE INSERT ON fee_reduction_requests
    FOR EACH ROW EXECUTE FUNCTION guard_fee_reduction_request();
