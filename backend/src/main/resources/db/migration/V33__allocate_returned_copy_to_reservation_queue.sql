-- S3-07.3: preserve reserved_at for FIFO; record the actual hold start separately.
-- Historical rows intentionally keep NULL rather than guessing an allocation time.
ALTER TABLE book_reservations
    ADD COLUMN hold_started_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_reservations_hold_started_at CHECK (
        hold_started_at IS NULL OR (
            book_copy_id IS NOT NULL AND pickup_deadline IS NOT NULL
            AND hold_started_at >= reserved_at AND pickup_deadline > hold_started_at
        )
    );

-- The existing V20 unique READY allocation and same-title FK remain the guards.
-- The service assigns the waiter under the title lock before marking the item returned.
-- Extend the original trigger without changing lending or immutable-history rules.
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
        OR NEW.borrowed_at <> OLD.borrowed_at OR OLD.returned_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Không được thay đổi danh tính hoặc chi tiết mượn đã trả.';
    END IF;
    IF TG_OP = 'INSERT' THEN
        SELECT reservation_id, borrower_user_id INTO source_reservation, borrower
        FROM loans WHERE id = NEW.loan_id;
        IF source_reservation IS NOT NULL THEN
            -- Lock reservation before copy, matching the service/cancellation order.
            SELECT book_copy_id, reader_id, status INTO held_copy, held_reader, reservation_status
            FROM book_reservations WHERE id = source_reservation FOR UPDATE;
            IF held_copy IS DISTINCT FROM NEW.book_copy_id OR held_reader IS DISTINCT FROM borrower
                OR reservation_status IS DISTINCT FROM 'READY_FOR_PICKUP' THEN
                RAISE EXCEPTION 'Phiếu mượn phải gắn đúng bạn đọc và bản sao của đơn Chờ nhận.';
            END IF;
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
