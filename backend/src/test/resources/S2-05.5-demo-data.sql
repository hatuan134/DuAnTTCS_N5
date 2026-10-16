-- Run manually on a local test database with V15 or newer; optimized backend requires V16.
-- Never an automatic migration. Also usable for the S2-05.4 baseline measurement.
-- Idempotent additions only. Uses sequences; never overwrites existing IDs.
\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;
SELECT pg_advisory_xact_lock(20505);
INSERT INTO warehouses(code, name, description)
SELECT 'S2055', 'Kho kiểm thử S2-05.5', 'Dữ liệu hiệu năng'
WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE lower(code) = 's2055');
INSERT INTO shelves(warehouse_id, code, name)
SELECT id, 'S2055', 'Kệ kiểm thử S2-05.5' FROM warehouses w WHERE lower(w.code) = 's2055'
AND NOT EXISTS (SELECT 1 FROM shelves s WHERE s.warehouse_id = w.id AND lower(s.code) = 's2055');
INSERT INTO categories(name, description, is_active)
SELECT 'S2055 Thể loại ' || n, 'Dữ liệu kiểm thử S2-05.5', true FROM generate_series(1, 4) n
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id IS NULL AND lower(c.name) = lower('S2055 Thể loại ' || n));
INSERT INTO authors(name, note, is_active)
SELECT 'S2055 Nguyễn Nhật Ánh ' || lpad(n::text, 3, '0'), 'Dữ liệu kiểm thử S2-05.5', true
FROM generate_series(1, 100) n
WHERE NOT EXISTS (SELECT 1 FROM authors a WHERE lower(a.name) = lower('S2055 Nguyễn Nhật Ánh ' || lpad(n::text, 3, '0')));

CREATE TEMP TABLE s2055_fixture ON COMMIT DROP AS
SELECT n,
    '9799901' || lpad(n::text, 6, '0') AS isbn,
    'S2055 ' || (ARRAY['Mắt biếc', 'Lập trình', 'Văn học', 'Kinh tế'])[1 + ((n-1) % 4)]
        || ' ' || lpad(n::text, 5, '0') AS title,
    2000 + ((n-1) % 25) AS year,
    a.id AS author_id, c.id AS category_id
FROM generate_series(1, 5000) n
JOIN authors a ON a.name = 'S2055 Nguyễn Nhật Ánh ' || lpad((1 + ((n-1) % 100))::text, 3, '0')
JOIN categories c ON c.parent_id IS NULL AND c.name = 'S2055 Thể loại ' || (1 + ((n-1) % 4));
-- Abort instead of accepting a collision with an unrelated book using the fixture ISBN.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM books b JOIN s2055_fixture f USING(isbn)
        WHERE b.title <> f.title OR b.author_id <> f.author_id OR b.category_id <> f.category_id
           OR b.publication_year IS DISTINCT FROM f.year) THEN
        RAISE EXCEPTION 'ISBN dữ liệu mẫu bị trùng hoặc dữ liệu mẫu đã đổi. Dùng database kiểm thử riêng.';
    END IF;
END $$;
INSERT INTO books(isbn, title, author_id, category_id, publisher, publication_year, page_count, description)
SELECT f.isbn, f.title, f.author_id, f.category_id, 'NXB Trẻ', f.year, 200, 'S2-05.5 performance fixture'
FROM s2055_fixture f WHERE NOT EXISTS (SELECT 1 FROM books b WHERE b.isbn = f.isbn);
INSERT INTO book_authors(book_id, author_id)
SELECT b.id, f.author_id FROM s2055_fixture f JOIN books b USING(isbn)
ON CONFLICT DO NOTHING;
INSERT INTO book_authors(book_id, author_id)
SELECT b.id, a.id FROM s2055_fixture f JOIN books b USING(isbn)
JOIN authors a ON a.name = 'S2055 Nguyễn Nhật Ánh ' || lpad((1 + (f.n % 100))::text, 3, '0')
WHERE f.n % 5 = 0 ON CONFLICT DO NOTHING;
-- Three copies per book: AVAILABLE/HELD/REPAIR/REMOVED mixtures. 1/3 have no free copy.
INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
SELECT b.id, 'S2055-' || lpad(f.n::text, 5, '0') || '-' || k,
    s.id,
    CASE WHEN k = 1 THEN CASE WHEN f.n % 3 = 0 THEN 'HELD' ELSE 'AVAILABLE' END
         WHEN k = 2 THEN 'REPAIR' ELSE 'REMOVED' END,
    (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 100000, 'GOOD'
FROM s2055_fixture f JOIN books b USING(isbn)
CROSS JOIN generate_series(1, 3) k
JOIN shelves s ON lower(s.code) = 's2055'
JOIN warehouses w ON w.id = s.warehouse_id AND lower(w.code) = 's2055'
ON CONFLICT(barcode) DO NOTHING;
DO $$ BEGIN
    IF (SELECT count(*) FROM s2055_fixture) <> 5000 OR
       (SELECT count(*) FROM book_copies WHERE barcode LIKE 'S2055-%') <> 15000 THEN
        RAISE EXCEPTION 'Dữ liệu mẫu không đủ 5000 đầu sách / 15000 bản sao.';
    END IF;
END $$;
COMMIT;
ANALYZE books;
ANALYZE authors;
ANALYZE book_authors;
ANALYZE book_copies;
ANALYZE loan_items;
SELECT count(*) AS fixture_books FROM books WHERE description = 'S2-05.5 performance fixture';
SELECT status, count(*) FROM book_copies WHERE barcode LIKE 'S2055-%' GROUP BY status ORDER BY status;
