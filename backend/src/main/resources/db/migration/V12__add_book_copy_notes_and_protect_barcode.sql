-- S2-03.1: nullable notes preserve existing copies without invented data.
ALTER TABLE book_copies ADD COLUMN notes VARCHAR(2000);

-- Protect identity even when an update bypasses the application.
CREATE FUNCTION protect_book_copy_barcode() RETURNS trigger AS $$
BEGIN
    IF NEW.barcode IS DISTINCT FROM OLD.barcode THEN
        RAISE EXCEPTION 'Không được thay đổi mã vạch của bản sao đã tồn tại'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_book_copies_protect_barcode
BEFORE UPDATE OF barcode ON book_copies
FOR EACH ROW EXECUTE FUNCTION protect_book_copy_barcode();
