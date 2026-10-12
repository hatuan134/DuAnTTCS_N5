-- One reservation source per item; retain loans.reservation_id for old clients/history.
-- No old rows are rewritten and no historical Flyway migration is changed.
ALTER TABLE loan_items
    ADD COLUMN source_reservation_id BIGINT,
    ADD CONSTRAINT fk_loan_items_source_reservation FOREIGN KEY (source_reservation_id)
        REFERENCES book_reservations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_loan_items_source_reservation UNIQUE (source_reservation_id);

-- Preserve V33 return/FIFO behavior and immutable item identity.
-- Old single-reservation callers still use the loan-header source on INSERT.
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
        UPDATE book_copies
        SET status = CASE WHEN EXISTS (
            SELECT 1 FROM book_reservations r
            WHERE r.book_copy_id = NEW.book_copy_id AND r.status = 'READY_FOR_PICKUP'
              AND r.hold_started_at = NEW.returned_at
        ) THEN 'HELD' ELSE 'AVAILABLE' END
        WHERE id = NEW.book_copy_id;
    END IF;
    RETURN NEW;
END;
$$;
