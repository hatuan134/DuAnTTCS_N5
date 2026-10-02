-- Dữ liệu kiểm thử S2-05.2. Chỉ chạy thủ công trên database local/test đã có V1..V15.
-- Không phải Flyway migration; không chạy tự động khi khởi động backend.
-- Không sửa/xóa đầu sách hoặc bản sao hiện có. Chạy lại không tạo trùng dữ liệu demo.
BEGIN;
DO $$
DECLARE
    demo_author_id BIGINT;
    literature_id BIGINT;
    technology_id BIGINT;
    demo_shelf_id BIGINT;
    demo_book_id BIGINT;
    item RECORD;
    copy_status TEXT;
    copy_number INTEGER;
BEGIN
    SELECT id INTO demo_author_id FROM authors WHERE name = 'Nguyễn Nhật Ánh' ORDER BY id LIMIT 1;
    SELECT id INTO literature_id FROM categories WHERE name = 'Văn học trong nước' ORDER BY id LIMIT 1;
    SELECT id INTO technology_id FROM categories WHERE name = 'Công nghệ thông tin' ORDER BY id LIMIT 1;
    SELECT id INTO demo_shelf_id FROM shelves ORDER BY id LIMIT 1;
    IF demo_author_id IS NULL OR literature_id IS NULL OR technology_id IS NULL OR demo_shelf_id IS NULL THEN
        RAISE EXCEPTION 'Thiếu tác giả, thể loại hoặc kệ mẫu. Hãy chạy project để Flyway tạo dữ liệu nền trước.';
    END IF;

    FOR item IN
        SELECT * FROM (VALUES
            ('A', 'S2052 DEMO A - Tuổi thơ', literature_id, 2008,
                ARRAY['AVAILABLE', 'AVAILABLE', 'BORROWED', 'HELD', 'REPAIR', 'REMOVED', 'LOST', 'DAMAGED']::TEXT[]),
            ('B', 'S2052 DEMO B - Bản cũ', literature_id, 1990,
                ARRAY['BORROWED', 'HELD', 'REPAIR', 'REMOVED']::TEXT[]),
            ('C', 'S2052 DEMO C - Lập trình', technology_id, 2008,
                ARRAY['AVAILABLE']::TEXT[]),
            ('D', 'S2052 DEMO D - Chưa có bản sao', literature_id, 2008, ARRAY[]::TEXT[])
        ) AS data(code, title, category_id, publication_year, statuses)
    LOOP
        SELECT id INTO demo_book_id FROM books WHERE title = item.title ORDER BY id LIMIT 1;
        IF demo_book_id IS NULL THEN
            INSERT INTO books (title, author_id, category_id, publisher, publication_year, page_count, description)
            VALUES (item.title, demo_author_id, item.category_id, 'NXB Trẻ', item.publication_year, 200,
                    'Dữ liệu demo lọc tra cứu S2-05.2') RETURNING id INTO demo_book_id;
        END IF;
        INSERT INTO book_authors (book_id, author_id) VALUES (demo_book_id, demo_author_id)
        ON CONFLICT DO NOTHING;

        copy_number := 0;
        FOREACH copy_status IN ARRAY item.statuses LOOP
            copy_number := copy_number + 1;
            INSERT INTO book_copies (book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
            VALUES (demo_book_id, 'S2052-DEMO-' || item.code || '-' || copy_number, demo_shelf_id, copy_status,
                    (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE, 100000, 'GOOD')
            ON CONFLICT (barcode) DO NOTHING;
        END LOOP;
    END LOOP;
END;
$$;
COMMIT;
