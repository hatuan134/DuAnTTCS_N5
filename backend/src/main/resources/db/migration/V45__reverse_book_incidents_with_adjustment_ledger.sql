-- S4-03.3: separate ledger; original incidents, fees and receipts stay immutable.
CREATE TABLE book_incident_adjustments (
 id BIGSERIAL PRIMARY KEY,
 adjustment_number TEXT GENERATED ALWAYS AS ('DC-SC-' || id::text) STORED UNIQUE,
 incident_id BIGINT NOT NULL UNIQUE REFERENCES book_incidents(id) ON DELETE RESTRICT,
 reason VARCHAR(1900) NOT NULL CHECK(length(regexp_replace(reason,'[[:space:]]','','g'))>0),
 case_type VARCHAR(30) NOT NULL CHECK(case_type IN ('MISTAKEN','FOUND','REASSESSED')),
 target_status VARCHAR(30) NOT NULL CHECK(target_status IN ('BORROWED','AVAILABLE','REPAIR')),
 requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT, requester_name VARCHAR(255) NOT NULL,
 requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CONFIRMED')),
 confirmed_by BIGINT REFERENCES users(id) ON DELETE RESTRICT, confirmer_name VARCHAR(255),
 confirmed_at TIMESTAMPTZ, confirmed_txid BIGINT,
 reversed_vnd BIGINT, cancelled_debt_vnd BIGINT, refund_due_vnd BIGINT,
 before_data JSONB, after_data JSONB,
 CHECK ((status='PENDING' AND confirmed_by IS NULL AND confirmed_at IS NULL AND confirmed_txid IS NULL
  AND reversed_vnd IS NULL AND cancelled_debt_vnd IS NULL AND refund_due_vnd IS NULL AND before_data IS NULL AND after_data IS NULL)
 OR (status='CONFIRMED' AND confirmed_by IS NOT NULL AND confirmer_name IS NOT NULL AND confirmed_at IS NOT NULL
  AND confirmed_txid IS NOT NULL AND reversed_vnd>=0 AND cancelled_debt_vnd>=0 AND refund_due_vnd>=0
  AND reversed_vnd=cancelled_debt_vnd+refund_due_vnd AND before_data IS NOT NULL AND after_data IS NOT NULL))
);
CREATE TRIGGER trg_adjustment_no_truncate BEFORE TRUNCATE ON book_incident_adjustments
 FOR EACH STATEMENT EXECUTE FUNCTION guard_book_incident_history();
CREATE OR REPLACE FUNCTION guard_incident_copy_status() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status <> 'REMOVED' AND EXISTS(SELECT 1 FROM book_incidents i WHERE i.copy_id=NEW.id
  AND NOT EXISTS(SELECT 1 FROM book_incident_adjustments a WHERE a.incident_id=i.id AND a.status='CONFIRMED')) THEN
  RAISE EXCEPTION 'Phải xác nhận bút toán điều chỉnh trước khi khôi phục bản sao.';
 END IF;
 RETURN NEW;
END; $$;
CREATE FUNCTION guard_reversed_fee_payment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.paid_amount_vnd IS DISTINCT FROM OLD.paid_amount_vnd AND EXISTS(
  SELECT 1 FROM book_incidents i JOIN book_incident_adjustments a ON a.incident_id=i.id
  WHERE i.compensation_fee_id=NEW.id AND a.status='CONFIRMED') THEN
  RAISE EXCEPTION 'Khoản bồi thường đã đảo không được thu thêm hoặc sửa tiền đã thu.';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_reversed_fee_payment BEFORE UPDATE ON reader_fees
 FOR EACH ROW EXECUTE FUNCTION guard_reversed_fee_payment();
CREATE OR REPLACE FUNCTION guard_loan_item_copy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    copy_status TEXT;
    source_reservation BIGINT;
    borrower BIGINT;
    held_copy BIGINT;
    held_reader BIGINT;
    reservation_status TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Không được xóa chi tiết phiếu mượn; hãy ghi nhận trả sách.';
    END IF;
    IF TG_OP='UPDATE' AND OLD.returned_at IS NOT NULL AND EXISTS (
      SELECT 1 FROM book_incidents i JOIN book_incident_adjustments a ON a.incident_id=i.id
      WHERE i.item_id=OLD.id AND a.status='CONFIRMED' AND a.confirmed_txid=txid_current()
       AND OLD.resolution_reason=i.incident_type AND OLD.returned_at=i.recorded_at
       AND ((a.target_status='BORROWED' AND NEW.returned_at IS NULL AND NEW.resolution_reason IS NULL
         AND NEW.returned_by IS NULL AND NEW.returned_by_name IS NULL)
       OR (a.target_status IN ('AVAILABLE','REPAIR') AND NEW.resolution_reason='RETURNED'
         AND NEW.returned_at=a.confirmed_at AND NEW.returned_by=a.confirmed_by AND NEW.returned_by_name=a.confirmer_name))
    ) AND (to_jsonb(NEW)-ARRAY['returned_at','returned_by','returned_by_name','resolution_reason'])
      = (to_jsonb(OLD)-ARRAY['returned_at','returned_by','returned_by_name','resolution_reason']) THEN
      RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.book_copy_id <> OLD.book_copy_id OR NEW.loan_id <> OLD.loan_id
        OR NEW.borrowed_at <> OLD.borrowed_at
        OR NEW.source_reservation_id IS DISTINCT FROM OLD.source_reservation_id
        OR (NEW.returned_at IS NULL AND NEW.resolution_reason IS NOT NULL)
        OR OLD.returned_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Không được thay đổi danh tính hoặc chi tiết mượn đã trả.';
    END IF;
    IF TG_OP = 'INSERT' THEN
        SELECT COALESCE(NEW.source_reservation_id, reservation_id), borrower_user_id INTO source_reservation, borrower
        FROM loans WHERE id = NEW.loan_id;
        IF source_reservation IS NOT NULL THEN
            -- Lock reservation before copy, matching the service/cancellation order.
            SELECT book_copy_id, reader_id, status INTO held_copy, held_reader, reservation_status
            FROM book_reservations WHERE id = source_reservation FOR UPDATE;
            IF held_copy IS DISTINCT FROM NEW.book_copy_id OR held_reader IS DISTINCT FROM borrower
                OR reservation_status IS DISTINCT FROM 'READY_FOR_PICKUP' THEN
                RAISE EXCEPTION 'Phiếu mượn phải gắn đúng bạn đọc và bản sao của đơn Chờ nhận.';
            END IF;
            NEW.source_reservation_id := source_reservation;
        END IF;
    END IF;
    SELECT status INTO copy_status FROM book_copies WHERE id = NEW.book_copy_id FOR UPDATE;
    IF TG_OP = 'INSERT' THEN
        IF NEW.resolution_reason IS NOT NULL THEN
            RAISE EXCEPTION 'Chi tiết phiếu mượn mới không được có lý do đóng.';
        END IF;
        IF NEW.returned_at IS NOT NULL THEN
            RAISE EXCEPTION 'Chi tiết phiếu mượn mới không được có thời điểm trả.';
        END IF;
        IF source_reservation IS NOT NULL THEN
            IF copy_status IS DISTINCT FROM 'HELD' THEN
                RAISE EXCEPTION 'Bản sao của đơn Chờ nhận phải đang ở trạng thái Đang giữ.';
            END IF;
        ELSIF copy_status IS DISTINCT FROM 'AVAILABLE' THEN
            RAISE EXCEPTION 'Chỉ bản sao Sẵn sàng mới được ghi nhận vào phiếu mượn mới.';
        END IF;
        UPDATE book_copies SET status = 'BORROWED' WHERE id = NEW.book_copy_id;
    ELSIF OLD.returned_at IS NULL AND NEW.returned_at IS NOT NULL THEN
        IF copy_status <> 'BORROWED' THEN
            RAISE EXCEPTION 'Trạng thái bản sao không khớp phiếu mượn chưa trả.';
        END IF;
        NEW.resolution_reason := COALESCE(NEW.resolution_reason, 'RETURNED');
        IF NEW.resolution_reason IN ('LOST', 'DAMAGED') AND NOT EXISTS (
            SELECT 1 FROM book_incidents i WHERE i.item_id=NEW.id AND i.copy_id=NEW.book_copy_id
                AND i.incident_type=NEW.resolution_reason AND i.recorded_at=NEW.returned_at
        ) THEN
            RAISE EXCEPTION 'Phải có sự cố đã xác nhận trước khi loại bản sao khỏi kho.';
        END IF;
        UPDATE book_copies
        SET status = CASE WHEN NEW.resolution_reason IN ('LOST','DAMAGED') THEN 'REMOVED' WHEN EXISTS (
            SELECT 1 FROM book_reservations r
            WHERE r.book_copy_id = NEW.book_copy_id AND r.status = 'READY_FOR_PICKUP'
              AND r.hold_started_at = NEW.returned_at
        ) THEN 'HELD' ELSE 'AVAILABLE' END
        WHERE id = NEW.book_copy_id;
    END IF;
    RETURN NEW;
END;
$$;


CREATE FUNCTION guard_incident_adjustment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE i book_incidents%ROWTYPE; f reader_fees%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' OR (TG_OP='UPDATE' AND OLD.status<>'PENDING') THEN
  RAISE EXCEPTION 'Không được sửa hoặc xóa lịch sử điều chỉnh.';
 END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING' OR NOT EXISTS(SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
   WHERE u.id=NEW.requested_by AND u.status='ACTIVE' AND r.code IN ('LIBRARIAN','LIBRARY_MANAGER','ADMIN')
    AND u.full_name=NEW.requester_name) THEN RAISE EXCEPTION 'Người lập bút toán không hợp lệ.'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.status<>'CONFIRMED' OR (to_jsonb(NEW)-ARRAY['status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
  'reversed_vnd','cancelled_debt_vnd','refund_due_vnd','before_data','after_data'])
 <> (to_jsonb(OLD)-ARRAY['status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
  'reversed_vnd','cancelled_debt_vnd','refund_due_vnd','before_data','after_data']) THEN
  RAISE EXCEPTION 'Chỉ được xác nhận yêu cầu, không được sửa nội dung đã lập.';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=NEW.confirmed_by
  AND u.status='ACTIVE' AND r.code IN ('LIBRARY_MANAGER','ADMIN') AND u.full_name=NEW.confirmer_name) THEN
  RAISE EXCEPTION 'Chỉ Quản lý hoặc Admin đang hoạt động được xác nhận.';
 END IF;
 SELECT * INTO STRICT i FROM book_incidents WHERE id=NEW.incident_id;
 SELECT * INTO STRICT f FROM reader_fees WHERE id=i.compensation_fee_id FOR UPDATE;
 IF NEW.before_data->'item' IS DISTINCT FROM (SELECT to_jsonb(li) FROM loan_items li WHERE li.id=i.item_id)
 OR NEW.before_data->'copy' IS DISTINCT FROM (SELECT to_jsonb(c) FROM book_copies c WHERE c.id=i.copy_id)
 OR NEW.before_data->'fee' IS DISTINCT FROM to_jsonb(f) THEN
  RAISE EXCEPTION 'Ảnh chụp dữ liệu trước điều chỉnh không khớp dữ liệu gốc.';
 END IF;
 IF f.waived_amount_vnd<>0 THEN RAISE EXCEPTION 'Khoản bồi thường có miễn giảm không phù hợp quy tắc đảo toàn bộ.'; END IF;
 IF NEW.confirmed_at IS NULL OR NEW.confirmed_at<OLD.requested_at OR NEW.confirmed_at>clock_timestamp() THEN
  RAISE EXCEPTION 'Thời điểm xác nhận không hợp lệ.';
 END IF;
 NEW.confirmed_txid:=txid_current();
 NEW.reversed_vnd:=i.compensation_vnd; NEW.cancelled_debt_vnd:=f.amount_vnd-f.paid_amount_vnd;
 NEW.refund_due_vnd:=f.paid_amount_vnd;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_adjustment_guard BEFORE INSERT OR UPDATE OR DELETE ON book_incident_adjustments
 FOR EACH ROW EXECUTE FUNCTION guard_incident_adjustment();
CREATE FUNCTION validate_incident_adjustment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE i book_incidents%ROWTYPE; li loan_items%ROWTYPE; c book_copies%ROWTYPE;
BEGIN
 IF NEW.status<>'CONFIRMED' THEN RETURN NULL; END IF;
 SELECT * INTO STRICT i FROM book_incidents WHERE id=NEW.incident_id;
 SELECT * INTO STRICT li FROM loan_items WHERE id=i.item_id;
 SELECT * INTO STRICT c FROM book_copies WHERE id=i.copy_id;
 IF (NEW.target_status='BORROWED' AND (li.returned_at IS NOT NULL OR c.status<>'BORROWED'))
 OR (NEW.target_status<>'BORROWED' AND (li.returned_at IS DISTINCT FROM NEW.confirmed_at OR li.resolution_reason<>'RETURNED'
  OR (NEW.target_status='REPAIR' AND c.status<>'REPAIR') OR (NEW.target_status='AVAILABLE' AND c.status NOT IN ('AVAILABLE','HELD'))))
 OR NEW.before_data->'item'->>'resolution_reason' IS DISTINCT FROM i.incident_type
 OR NEW.before_data->'copy'->>'status' IS DISTINCT FROM 'REMOVED'
 OR NEW.after_data->'item' IS DISTINCT FROM to_jsonb(li)
 OR NEW.after_data->'copy' IS DISTINCT FROM to_jsonb(c)
 OR NEW.after_data->'fee' IS DISTINCT FROM (SELECT to_jsonb(f) FROM reader_fees f WHERE f.id=i.compensation_fee_id)
 OR NOT EXISTS(SELECT 1 FROM audit_logs WHERE action='INCIDENT_REVERSED' AND entity_type='BOOK_INCIDENT_ADJUSTMENT' AND entity_id=NEW.id::text)
 THEN RAISE EXCEPTION 'Bút toán, bản sao, phiếu mượn hoặc nhật ký không khớp.'; END IF;
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER trg_adjustment_complete AFTER UPDATE ON book_incident_adjustments
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_incident_adjustment();

CREATE FUNCTION guard_incident_adjustment_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='TRUNCATE' THEN RAISE EXCEPTION 'Không được xóa toàn bộ nhật ký đối soát.'; END IF;
 IF OLD.action IN ('INCIDENT_REVERSED','INCIDENT_ADJUSTMENT_REQUESTED') THEN RAISE EXCEPTION 'Không được sửa hoặc xóa nhật ký bút toán điều chỉnh.'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_adjustment_audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
 FOR EACH ROW EXECUTE FUNCTION guard_incident_adjustment_audit();
CREATE TRIGGER trg_adjustment_audit_no_truncate BEFORE TRUNCATE ON audit_logs
 FOR EACH STATEMENT EXECUTE FUNCTION guard_incident_adjustment_audit();
