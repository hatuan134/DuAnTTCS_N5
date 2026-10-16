-- =========================================================
-- V4__implement_s1_09_library_configuration.sql
-- S1-09: Kho, kệ, lịch làm việc và ngày đóng cửa
-- =========================================================

-- 1. Bản sao sách tối thiểu để kiểm soát kệ đang được sử dụng.
-- Bảng này là nền tảng cho các sprint mượn/trả sau.
CREATE TABLE IF NOT EXISTS book_copies (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL,
    barcode VARCHAR(100) NOT NULL UNIQUE,
    shelf_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_book_copies_book
        FOREIGN KEY (book_id)
        REFERENCES books(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_book_copies_shelf
        FOREIGN KEY (shelf_id)
        REFERENCES shelves(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_book_copies_status
        CHECK (status IN ('AVAILABLE', 'BORROWED', 'HELD', 'LOST', 'DAMAGED'))
);

CREATE INDEX IF NOT EXISTS ix_book_copies_book_id ON book_copies(book_id);
CREATE INDEX IF NOT EXISTS ix_book_copies_shelf_id ON book_copies(shelf_id);

DROP TRIGGER IF EXISTS trg_book_copies_updated_at ON book_copies;
CREATE TRIGGER trg_book_copies_updated_at
BEFORE UPDATE ON book_copies
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- 2. Lịch làm việc mặc định đủ 7 ngày.
INSERT INTO library_weekly_schedule (day_of_week, is_open, open_time, close_time)
VALUES
    (1, TRUE,  '08:00', '17:00'),
    (2, TRUE,  '08:00', '17:00'),
    (3, TRUE,  '08:00', '17:00'),
    (4, TRUE,  '08:00', '17:00'),
    (5, TRUE,  '08:00', '17:00'),
    (6, TRUE,  '08:00', '12:00'),
    (7, FALSE, NULL,    NULL)
ON CONFLICT (day_of_week) DO NOTHING;

-- 3. Dữ liệu mẫu kho/kệ phục vụ demo S1-09.
INSERT INTO warehouses (code, name, description)
SELECT 'KHO-A', 'Kho A', 'Kho sách chính của thư viện.'
WHERE NOT EXISTS (
    SELECT 1 FROM warehouses WHERE LOWER(code) = LOWER('KHO-A')
);

INSERT INTO warehouses (code, name, description)
SELECT 'KHO-B', 'Kho B', 'Kho sách tham khảo.'
WHERE NOT EXISTS (
    SELECT 1 FROM warehouses WHERE LOWER(code) = LOWER('KHO-B')
);

INSERT INTO shelves (warehouse_id, code, name, description)
SELECT w.id, 'A01', 'Kệ Văn học', 'Kệ mẫu có bản sao sách để kiểm thử chặn xoá.'
FROM warehouses w
WHERE LOWER(w.code) = LOWER('KHO-A')
  AND NOT EXISTS (
      SELECT 1 FROM shelves s
      WHERE s.warehouse_id = w.id AND LOWER(s.code) = LOWER('A01')
  );

INSERT INTO shelves (warehouse_id, code, name, description)
SELECT w.id, 'A02', 'Kệ Công nghệ', 'Kệ trống có thể xoá để kiểm thử.'
FROM warehouses w
WHERE LOWER(w.code) = LOWER('KHO-A')
  AND NOT EXISTS (
      SELECT 1 FROM shelves s
      WHERE s.warehouse_id = w.id AND LOWER(s.code) = LOWER('A02')
  );

INSERT INTO shelves (warehouse_id, code, name, description)
SELECT w.id, 'B01', 'Kệ Tham khảo', 'Kệ sách tham khảo.'
FROM warehouses w
WHERE LOWER(w.code) = LOWER('KHO-B')
  AND NOT EXISTS (
      SELECT 1 FROM shelves s
      WHERE s.warehouse_id = w.id AND LOWER(s.code) = LOWER('B01')
  );

-- Một bản sao mẫu tại A01 để kiểm thử quy tắc "không xoá kệ đang có bản sao".
INSERT INTO book_copies (book_id, barcode, shelf_id, status)
SELECT b.id, 'LIBRA-DEMO-0001', s.id, 'AVAILABLE'
FROM books b
JOIN shelves s ON LOWER(s.code) = LOWER('A01')
JOIN warehouses w ON w.id = s.warehouse_id AND LOWER(w.code) = LOWER('KHO-A')
WHERE b.id = (SELECT MIN(id) FROM books)
  AND NOT EXISTS (SELECT 1 FROM book_copies WHERE barcode = 'LIBRA-DEMO-0001');

-- 4. Một vài ngày đóng cửa mẫu năm 2026.
INSERT INTO library_closed_dates (closed_date, reason)
VALUES
    ('2026-01-01', 'Tết Dương lịch'),
    ('2026-09-02', 'Quốc khánh')
ON CONFLICT (closed_date) DO NOTHING;
