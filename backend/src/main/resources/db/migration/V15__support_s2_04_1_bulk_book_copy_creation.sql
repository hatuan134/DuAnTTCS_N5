-- S2-04.1: Tạo nhiều bản sao cùng lúc chỉ yêu cầu số lượng, kho, kệ và ngày nhập.
-- Giữ nguyên quy tắc S2-02.1 cho luồng tạo một bản sao: giá bìa và tình trạng vật lý vẫn bắt buộc.
-- Chỉ giao dịch bulk do backend đánh dấu bằng setting cục bộ mới được để hai trường này NULL.

CREATE OR REPLACE FUNCTION protect_book_copy_details() RETURNS trigger AS $$
DECLARE
    bulk_creation BOOLEAN := COALESCE(current_setting('app.bulk_book_copy_creation', TRUE), '') = 'true';
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.book_id IS DISTINCT FROM OLD.book_id THEN
            RAISE EXCEPTION 'Không được thay đổi đầu sách của bản sao đã tồn tại'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NOT bulk_creation
           AND (NEW.received_date IS NULL OR NEW.cover_price IS NULL OR NEW.physical_condition IS NULL) THEN
            RAISE EXCEPTION 'Bản sao mới phải có ngày nhập, giá bìa và tình trạng vật lý'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.received_date IS NULL
           OR NEW.received_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
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
