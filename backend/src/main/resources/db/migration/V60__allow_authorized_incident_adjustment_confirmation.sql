-- S4-09.2 dependency: valid ADMIN/MANAGER confirmation failed in PostgreSQL 15.
-- adjustment_number is GENERATED ALWAYS AS from id and is NULL in NEW during BEFORE UPDATE.
-- Exclude that generated value from the immutable request comparison. id and all request fields
-- remain compared; all actor, snapshot, amount and history guards remain unchanged.
CREATE OR REPLACE FUNCTION guard_incident_adjustment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
 IF NEW.status<>'CONFIRMED' OR (to_jsonb(NEW)-ARRAY['adjustment_number','status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
  'reversed_vnd','cancelled_debt_vnd','refund_due_vnd','before_data','after_data'])
 <> (to_jsonb(OLD)-ARRAY['adjustment_number','status','confirmed_by','confirmer_name','confirmed_at','confirmed_txid',
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
