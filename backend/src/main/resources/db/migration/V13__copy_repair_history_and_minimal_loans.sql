-- Minimal lending data authorized for S2-03.2; no lending UI/API in this slice.
CREATE TABLE loans (
    id BIGSERIAL PRIMARY KEY,
    loan_number VARCHAR(100) NOT NULL UNIQUE,
    borrower_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    borrowed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE loan_items (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    book_copy_id BIGINT NOT NULL REFERENCES book_copies(id) ON DELETE RESTRICT,
    borrowed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    returned_at TIMESTAMPTZ,
    CONSTRAINT ck_loan_item_return_time CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    UNIQUE (loan_id, book_copy_id)
);
CREATE UNIQUE INDEX ux_copy_unreturned_loan ON loan_items(book_copy_id) WHERE returned_at IS NULL;
CREATE INDEX ix_loan_items_loan ON loan_items(loan_id);

-- Both lending and repair serialize on the same copy row.
CREATE FUNCTION guard_loan_item_copy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE copy_status TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Không được xóa chi tiết phiếu mượn; hãy ghi nhận trả sách.';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.book_copy_id <> OLD.book_copy_id OR NEW.loan_id <> OLD.loan_id
        OR NEW.borrowed_at <> OLD.borrowed_at OR OLD.returned_at IS NOT NULL) THEN
        RAISE EXCEPTION 'Không được thay đổi danh tính hoặc chi tiết mượn đã trả.';
    END IF;
    SELECT status INTO copy_status FROM book_copies WHERE id = NEW.book_copy_id FOR UPDATE;
    IF TG_OP = 'INSERT' THEN
        IF NEW.returned_at IS NOT NULL OR copy_status <> 'AVAILABLE' THEN
            RAISE EXCEPTION 'Chỉ bản sao Sẵn sàng mới được ghi nhận vào phiếu mượn mới.';
        END IF;
        UPDATE book_copies SET status = 'BORROWED' WHERE id = NEW.book_copy_id;
    ELSIF OLD.returned_at IS NULL AND NEW.returned_at IS NOT NULL THEN
        IF copy_status <> 'BORROWED' THEN
            RAISE EXCEPTION 'Trạng thái bản sao không khớp phiếu mượn chưa trả.';
        END IF;
        UPDATE book_copies SET status = 'AVAILABLE' WHERE id = NEW.book_copy_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_loan_item_copy BEFORE INSERT OR UPDATE OR DELETE ON loan_items
FOR EACH ROW EXECUTE FUNCTION guard_loan_item_copy();

CREATE TABLE book_copy_status_history (
    id BIGSERIAL PRIMARY KEY,
    book_copy_id BIGINT NOT NULL REFERENCES book_copies(id) ON DELETE RESTRICT,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_name VARCHAR(255) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    reason VARCHAR(2000) NOT NULL CHECK (length(btrim(reason)) > 0),
    CHECK (previous_status <> new_status)
);
CREATE INDEX ix_copy_status_history ON book_copy_status_history(book_copy_id, changed_at DESC, id DESC);
CREATE FUNCTION protect_copy_status_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Không được sửa hoặc xóa lịch sử thay đổi trạng thái bản sao.';
END;
$$;
CREATE TRIGGER trg_protect_copy_status_history BEFORE UPDATE OR DELETE ON book_copy_status_history
FOR EACH ROW EXECUTE FUNCTION protect_copy_status_history();
CREATE TRIGGER trg_protect_copy_status_history_truncate BEFORE TRUNCATE ON book_copy_status_history
FOR EACH STATEMENT EXECUTE FUNCTION protect_copy_status_history();
