-- S4-03.1: per-item closure; never rewrite historical returns or recorded fees.
ALTER TABLE loan_items ADD COLUMN resolution_reason VARCHAR(30);
ALTER TABLE loan_items ADD CONSTRAINT ck_loan_item_resolution_reason CHECK (
    resolution_reason IS NULL OR (returned_at IS NOT NULL AND resolution_reason IN ('RETURNED','LOST','DAMAGED'))
);
CREATE TABLE library_compensation_settings (
    id SMALLINT PRIMARY KEY CHECK(id=1),
    lost_factor NUMERIC(12,2) CHECK(lost_factor>0),
    damaged_factor NUMERIC(12,2) CHECK(damaged_factor>0),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ
);
INSERT INTO library_compensation_settings(id) VALUES(1);
ALTER TABLE reader_fees ADD COLUMN compensation_item_id BIGINT UNIQUE REFERENCES loan_items(id) ON DELETE RESTRICT;
ALTER TABLE reader_fees DROP CONSTRAINT reader_fees_amount_vnd_check;
ALTER TABLE reader_fees ADD CONSTRAINT reader_fees_amount_vnd_check CHECK(
    amount_vnd>0 OR (amount_vnd=0 AND compensation_item_id IS NOT NULL)
);
ALTER TABLE reader_fees ADD CONSTRAINT ck_reader_fee_kind CHECK(return_item_id IS NULL OR compensation_item_id IS NULL);
CREATE TABLE book_incidents (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL UNIQUE REFERENCES loan_items(id) ON DELETE RESTRICT,
    copy_id BIGINT NOT NULL UNIQUE REFERENCES book_copies(id) ON DELETE RESTRICT,
    reader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    incident_type VARCHAR(30) NOT NULL CHECK(incident_type IN ('LOST','DAMAGED')),
    description VARCHAR(1900) NOT NULL CHECK(length(btrim(description))>0),
    cover_price NUMERIC(12,2) NOT NULL CHECK(cover_price>0),
    factor NUMERIC(12,2) NOT NULL CHECK(factor>0),
    compensation_vnd BIGINT NOT NULL CHECK(compensation_vnd BETWEEN 0 AND 9007199254740991),
    compensation_fee_id BIGINT NOT NULL UNIQUE REFERENCES reader_fees(id) ON DELETE RESTRICT,
    actor_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_name VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_txid BIGINT NOT NULL DEFAULT txid_current(),
    before_status VARCHAR(30) NOT NULL CHECK(before_status='BORROWED'),
    after_status VARCHAR(30) NOT NULL CHECK(after_status='REMOVED'),
    CHECK(compensation_vnd=round(cover_price*factor,0))
);
CREATE TABLE book_incident_images (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES book_incidents(id) ON DELETE RESTRICT,
    filename VARCHAR(200) NOT NULL,
    content_type VARCHAR(30) NOT NULL CHECK(content_type IN ('image/jpeg','image/png','image/webp')),
    image_data BYTEA NOT NULL CHECK(octet_length(image_data) BETWEEN 1 AND 5242880)
);
CREATE INDEX ix_incidents_time ON book_incidents(recorded_at DESC,id DESC);
CREATE INDEX ix_incident_images ON book_incident_images(incident_id,id);
CREATE FUNCTION guard_book_incident_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Không được sửa hoặc xóa sự cố và ảnh đã xác nhận.';
END;
$$;
CREATE TRIGGER trg_incident_immutable BEFORE UPDATE OR DELETE ON book_incidents
    FOR EACH ROW EXECUTE FUNCTION guard_book_incident_history();
CREATE TRIGGER trg_incident_images_immutable BEFORE UPDATE OR DELETE ON book_incident_images
    FOR EACH ROW EXECUTE FUNCTION guard_book_incident_history();
CREATE TRIGGER trg_incident_no_truncate BEFORE TRUNCATE ON book_incidents
    FOR EACH STATEMENT EXECUTE FUNCTION guard_book_incident_history();
CREATE TRIGGER trg_incident_images_no_truncate BEFORE TRUNCATE ON book_incident_images
    FOR EACH STATEMENT EXECUTE FUNCTION guard_book_incident_history();
CREATE FUNCTION guard_incident_compensation_fee() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.compensation_item_id IS NOT NULL THEN RAISE EXCEPTION 'Không được xóa khoản bồi thường.'; END IF;
        RETURN OLD;
    END IF;
    IF OLD.compensation_item_id IS NOT NULL AND (
        NEW.compensation_item_id IS DISTINCT FROM OLD.compensation_item_id
        OR NEW.reader_user_id IS DISTINCT FROM OLD.reader_user_id OR NEW.amount_vnd IS DISTINCT FROM OLD.amount_vnd
        OR NEW.return_item_id IS DISTINCT FROM OLD.return_item_id OR NEW.waived_amount_vnd IS DISTINCT FROM OLD.waived_amount_vnd) THEN
        RAISE EXCEPTION 'Không được thay đổi khoản bồi thường đã ghi nhận.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_incident_fee_immutable BEFORE UPDATE OR DELETE ON reader_fees
    FOR EACH ROW EXECUTE FUNCTION guard_incident_compensation_fee();
-- Existing payment snapshot types are extended only for the new receivable.
ALTER TABLE fee_payment_items DROP CONSTRAINT fee_payment_items_fee_type_check;
ALTER TABLE fee_payment_items ADD CONSTRAINT fee_payment_items_fee_type_check CHECK(fee_type IN ('OVERDUE','RECORDED','COMPENSATION'));
-- An incident copy can never be made available again through old copy editing.
CREATE FUNCTION guard_incident_copy_status() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status <> 'REMOVED' AND EXISTS(SELECT 1 FROM book_incidents WHERE copy_id=NEW.id) THEN
        RAISE EXCEPTION 'Bản sao đã ghi nhận sự cố phải giữ trạng thái Đã loại khỏi kho.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_incident_copy_status BEFORE UPDATE ON book_copies
    FOR EACH ROW EXECUTE FUNCTION guard_incident_copy_status();

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

-- The incident, item closure and receivable must agree at commit, including direct SQL writes.
CREATE FUNCTION validate_book_incident_links() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM loan_items li JOIN loans l ON l.id=li.loan_id
        JOIN book_copies c ON c.id=li.book_copy_id JOIN reader_fees f ON f.id=NEW.compensation_fee_id
        WHERE li.id=NEW.item_id AND li.book_copy_id=NEW.copy_id AND l.borrower_user_id=NEW.reader_user_id
          AND li.returned_at=NEW.recorded_at AND li.resolution_reason=NEW.incident_type AND c.status='REMOVED'
          AND f.compensation_item_id=NEW.item_id AND f.reader_user_id=NEW.reader_user_id AND f.amount_vnd=NEW.compensation_vnd
    ) THEN
        RAISE EXCEPTION 'Sự cố, khoản bồi thường, lượt mượn và trạng thái kho không khớp.';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_incident_complete AFTER INSERT ON book_incidents
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_book_incident_links();
CREATE FUNCTION guard_incident_image_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE recorded_txid BIGINT;
BEGIN
    SELECT created_txid INTO recorded_txid FROM book_incidents WHERE id=NEW.incident_id FOR UPDATE;
    IF recorded_txid IS DISTINCT FROM txid_current() THEN
        RAISE EXCEPTION 'Không được thêm ảnh vào sự cố đã xác nhận.';
    END IF;
    IF (SELECT count(*) FROM book_incident_images WHERE incident_id=NEW.incident_id)>=5 THEN
        RAISE EXCEPTION 'Chỉ được đính kèm tối đa 5 ảnh.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_incident_image_insert BEFORE INSERT ON book_incident_images
    FOR EACH ROW EXECUTE FUNCTION guard_incident_image_insert();
