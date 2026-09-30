-- S2-02.1. Preserve all legacy copies; unknown historical data remains NULL.
ALTER TABLE book_copies
    ADD COLUMN received_date DATE,
    ADD COLUMN cover_price NUMERIC(12, 2),
    ADD COLUMN physical_condition VARCHAR(30),
    ADD CONSTRAINT ck_book_copies_cover_price CHECK (cover_price >= 0),
    ADD CONSTRAINT ck_book_copies_physical_condition CHECK (
        physical_condition IN ('NEW', 'GOOD', 'OLD', 'LIGHTLY_DAMAGED', 'HEAVILY_DAMAGED')
    );

-- Warehouse is stored through the existing shelf_id -> shelves.warehouse_id relation.
-- New inserts must be complete, without inventing values for existing records.
CREATE FUNCTION protect_book_copy_details() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.book_id IS DISTINCT FROM OLD.book_id THEN
            RAISE EXCEPTION 'Không được thay đổi đầu sách của bản sao đã tồn tại'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.received_date IS NULL OR NEW.cover_price IS NULL OR NEW.physical_condition IS NULL THEN
            RAISE EXCEPTION 'Bản sao mới phải có ngày nhập, giá bìa và tình trạng vật lý'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.received_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
           OR NEW.received_date < DATE '0001-01-01' THEN
            RAISE EXCEPTION 'Ngày nhập không hợp lệ' USING ERRCODE = '23514';
        END IF;
        IF btrim(NEW.barcode) = '' OR NEW.barcode <> btrim(NEW.barcode) THEN
            RAISE EXCEPTION 'Mã vạch không hợp lệ' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_book_copies_protect_details
BEFORE INSERT OR UPDATE ON book_copies
FOR EACH ROW EXECUTE FUNCTION protect_book_copy_details();
