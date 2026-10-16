-- Local/demo only. Run AFTER Flyway V13. Each run creates a new isolated demo set.
-- Does not reset, delete or overwrite existing loans/copies/history.
BEGIN;
DO $$
DECLARE
    tag TEXT := 'S2032-' || txid_current();
    staff_id BIGINT; reader_id BIGINT; author_id_value BIGINT; category_id_value BIGINT;
    book_id_value BIGINT; warehouse_id_value BIGINT; shelf_id_value BIGINT;
    copy_id_value BIGINT; loan_id_value BIGINT; n INTEGER;
BEGIN
    SELECT u.id INTO staff_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.name IN ('LIBRARIAN','LIBRARY_MANAGER','ADMIN') AND u.status = 'ACTIVE' ORDER BY u.id LIMIT 1;
    IF staff_id IS NULL THEN RAISE EXCEPTION 'Cần khởi động backend và có ít nhất một tài khoản nhân viên hoạt động.'; END IF;
    INSERT INTO users(role_id, full_name, email, status)
        SELECT id, 'Bạn đọc dữ liệu ' || tag, lower(tag) || '@example.invalid', 'DISABLED'
        FROM roles WHERE name = 'READER' RETURNING id INTO reader_id;
    IF reader_id IS NULL THEN RAISE EXCEPTION 'Không tìm thấy vai trò READER.'; END IF;
    INSERT INTO authors(name) VALUES ('Tác giả kiểm thử ' || tag) RETURNING id INTO author_id_value;
    SELECT id INTO category_id_value FROM categories WHERE is_active = TRUE ORDER BY id LIMIT 1;
    IF category_id_value IS NULL THEN RAISE EXCEPTION 'Cần một thể loại đang hoạt động.'; END IF;
    INSERT INTO books(title, author_id, category_id, publisher, publication_year, page_count)
        VALUES ('Kiểm thử sửa chữa ' || tag, author_id_value, category_id_value, 'NXB Kiểm thử',
        extract(year FROM CURRENT_DATE)::integer, 120) RETURNING id INTO book_id_value;
    INSERT INTO book_authors(book_id, author_id) VALUES (book_id_value, author_id_value);
    INSERT INTO warehouses(code, name) VALUES(tag, 'Kho kiểm thử ' || tag) RETURNING id INTO warehouse_id_value;
    INSERT INTO shelves(warehouse_id, code, name) VALUES(warehouse_id_value, 'A01', 'Kệ kiểm thử') RETURNING id INTO shelf_id_value;
    FOR n IN 1..3 LOOP
        INSERT INTO book_copies(book_id, barcode, shelf_id, received_date, cover_price, physical_condition)
            VALUES(book_id_value, tag || '-' || n, shelf_id_value,
            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 85000, 'GOOD') RETURNING id INTO copy_id_value;
        IF n = 3 THEN
            INSERT INTO loans(loan_number, borrower_user_id, created_by)
                VALUES(tag || '-PM', reader_id, staff_id) RETURNING id INTO loan_id_value;
            INSERT INTO loan_items(loan_id, book_copy_id) VALUES(loan_id_value, copy_id_value);
        END IF;
        RAISE NOTICE 'Bản %: http://localhost:5173/book-copies/%', n, copy_id_value;
    END LOOP;
    RAISE NOTICE 'Đầu sách: http://localhost:5173/books/%; tìm công khai bằng từ khóa: %', book_id_value, tag;
    RAISE NOTICE 'Bản -1 và -2 Sẵn sàng; bản -3 thuộc phiếu mượn chưa trả. Ban đầu có 2 Sẵn sàng / 3 bản sao.';
END $$;
COMMIT;
SELECT c.id, b.title, c.barcode, c.status
FROM book_copies c JOIN books b ON b.id = c.book_id
WHERE c.barcode LIKE 'S2032-%' ORDER BY c.id DESC LIMIT 12;
