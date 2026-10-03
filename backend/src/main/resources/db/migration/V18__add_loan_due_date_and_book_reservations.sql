-- V18__add_loan_due_date_and_book_reservations.sql
-- S2-06.3: Hiển thị hàng đợi đặt giữ và ngày dự kiến có sách khi hết bản

-- 1. Bổ sung trường ngày hẹn trả (due_date) vào bảng loan_items nếu chưa có
ALTER TABLE loan_items
    ADD COLUMN IF NOT EXISTS due_date TIMESTAMPTZ;

-- 2. Bảng book_reservations (hàng đợi đặt giữ đầu sách)
CREATE TABLE IF NOT EXISTS book_reservations (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    reader_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pickup_deadline TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    CONSTRAINT ck_reservation_status CHECK (
        status IN ('PENDING', 'READY_FOR_PICKUP', 'FULFILLED', 'CANCELLED', 'EXPIRED')
    )
);

CREATE INDEX IF NOT EXISTS ix_reservations_book_status 
    ON book_reservations(book_id, status);

CREATE INDEX IF NOT EXISTS ix_reservations_reader 
    ON book_reservations(reader_id);

CREATE INDEX IF NOT EXISTS ix_loan_items_copy_unreturned_due 
    ON loan_items(book_copy_id, due_date) 
    WHERE returned_at IS NULL;
