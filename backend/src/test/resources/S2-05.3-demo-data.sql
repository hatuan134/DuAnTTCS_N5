-- S2-05.3: chỉ chạy thủ công trên database local/test đã có V1..V15.
-- Không phải Flyway migration. Không sửa/xóa dữ liệu hiện có.
-- Chạy lại không thêm trùng các bản ghi demo.
BEGIN;
DO $$
DECLARE
    regular_author BIGINT;
    matching_author BIGINT;
    category_id_demo BIGINT;
    shelf_id_demo BIGINT;
    book_id_demo BIGINT;
    group_name TEXT;
    group_count INTEGER;
    i INTEGER;
    title_demo TEXT;
    year_demo INTEGER;
    author_id_demo BIGINT;
BEGIN
    SELECT id INTO category_id_demo FROM categories WHERE name = 'Văn học trong nước' ORDER BY id LIMIT 1;
    SELECT id INTO shelf_id_demo FROM shelves ORDER BY id LIMIT 1;
    IF category_id_demo IS NULL OR shelf_id_demo IS NULL THEN
        RAISE EXCEPTION 'Thiếu thể loại/kệ mẫu. Chạy project để Flyway tạo dữ liệu nền trước.';
    END IF;

    SELECT id INTO regular_author FROM authors WHERE name = 'S2053 Tác giả demo' ORDER BY id LIMIT 1;
    IF regular_author IS NULL THEN
        INSERT INTO authors(name, note, is_active) VALUES ('S2053 Tác giả demo', 'Kiểm thử S2-05.3', TRUE)
        RETURNING id INTO regular_author;
    END IF;
    SELECT id INTO matching_author FROM authors WHERE name = 'S2053 RELEVANCE' ORDER BY id LIMIT 1;
    IF matching_author IS NULL THEN
        INSERT INTO authors(name, note, is_active) VALUES ('S2053 RELEVANCE', 'Kiểm thử mức phù hợp S2-05.3', TRUE)
        RETURNING id INTO matching_author;
    END IF;

    FOREACH group_name IN ARRAY ARRAY['LT20', 'EQ20', 'MANY', 'SCORE'] LOOP
        group_count := CASE group_name WHEN 'LT20' THEN 19 WHEN 'EQ20' THEN 20 WHEN 'MANY' THEN 45 ELSE 4 END;
        FOR i IN 1..group_count LOOP
            title_demo := 'S2053 ' || group_name || ' ' || LPAD(i::TEXT, 3, '0');
            author_id_demo := regular_author;
            year_demo := CASE WHEN group_name = 'MANY' AND i > 25 THEN 1990 ELSE 2008 END;
            IF group_name = 'SCORE' THEN
                title_demo := CASE i
                    WHEN 1 THEN 'S2053 RELEVANCE bản mở rộng'
                    WHEN 2 THEN 'S2053 RELEVANCE'
                    WHEN 3 THEN 's2053 relevance'
                    ELSE 'S2053 SCORE khớp tác giả' END;
                IF i IN (3, 4) THEN author_id_demo := matching_author; END IF;
            END IF;
            SELECT id INTO book_id_demo FROM books WHERE title = title_demo ORDER BY id LIMIT 1;
            IF book_id_demo IS NULL THEN
                INSERT INTO books(title, author_id, category_id, publisher, publication_year, page_count, description)
                VALUES (title_demo, author_id_demo, category_id_demo, 'NXB Trẻ', year_demo, 200, 'Dữ liệu demo S2-05.3')
                RETURNING id INTO book_id_demo;
            END IF;
            INSERT INTO book_authors(book_id, author_id) VALUES (book_id_demo, author_id_demo) ON CONFLICT DO NOTHING;
            INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
            VALUES (book_id_demo, 'S2053-' || group_name || '-' || i, shelf_id_demo,
                    CASE WHEN group_name = 'MANY' AND i > 41 THEN 'BORROWED' ELSE 'AVAILABLE' END,
                    (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE, 100000, 'GOOD')
            ON CONFLICT (barcode) DO NOTHING;
        END LOOP;
    END LOOP;

    -- Đầu sách cùng từ khóa MANY nhưng chưa có bản sao, phải được ẩn trước khi đếm/phân trang.
    IF NOT EXISTS (SELECT 1 FROM books WHERE title = 'S2053 MANY chưa có bản sao') THEN
        INSERT INTO books(title, author_id, category_id, publisher, publication_year, page_count, description)
        VALUES ('S2053 MANY chưa có bản sao', regular_author, category_id_demo, 'NXB Trẻ', 2008, 200, 'Dữ liệu demo S2-05.3');
    END IF;
END;
$$;
COMMIT;
