-- S3-04.3: run manually ONCE on a disposable demo database, never a production database.
-- First prepare an ACTIVE demo reader. In the same SQL session run:
-- SET demo.reader_email = 'your-active-demo-reader@example.invalid';
-- Requires an ACTIVE staff account, at least one book and one shelf from existing setup.
-- Creates 21 completed returns plus one outstanding line in a partially returned loan.
-- No existing rows are updated or deleted; the normal lending triggers are exercised.
BEGIN;
DO $$
DECLARE
    demo_reader_email TEXT := NULLIF(current_setting('demo.reader_email', true), '');
    demo_reader_id BIGINT;
    demo_staff_id BIGINT;
    demo_book_id BIGINT;
    demo_shelf_id BIGINT;
    demo_copy_id BIGINT;
    demo_loan_id BIGINT;
    demo_partial_loan_id BIGINT;
    demo_run_key TEXT := md5(clock_timestamp()::text || random()::text);
    demo_borrowed TIMESTAMPTZ := CURRENT_TIMESTAMP - INTERVAL '60 days';
    demo_returned TIMESTAMPTZ;
    i INTEGER;
BEGIN
    SELECT u.id INTO demo_reader_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE lower(u.email) = lower(demo_reader_email) AND r.code = 'READER' AND u.status = 'ACTIVE';
    SELECT u.id INTO demo_staff_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code IN ('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN') AND u.status = 'ACTIVE' ORDER BY u.id LIMIT 1;
    SELECT MIN(id) INTO demo_book_id FROM books;
    SELECT MIN(id) INTO demo_shelf_id FROM shelves;
    IF demo_reader_id IS NULL OR demo_staff_id IS NULL OR demo_book_id IS NULL OR demo_shelf_id IS NULL THEN
        RAISE EXCEPTION 'Chuẩn bị Bạn đọc demo ACTIVE, nhân viên ACTIVE, đầu sách và kệ trước khi chạy.';
    END IF;
    IF EXISTS (SELECT 1 FROM loans WHERE borrower_user_id = demo_reader_id AND loan_number LIKE 'DEMO-S3043-%') THEN
        RAISE EXCEPTION 'Bạn đọc đã có dữ liệu demo S3-04.3. Không chạy lại để tránh tạo lịch sử trùng.';
    END IF;
    FOR i IN 1..21 LOOP
        demo_returned := CURRENT_TIMESTAMP - (i - 1) * INTERVAL '1 day';
        INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
            VALUES (demo_book_id, 'DEMO-S3043-' || demo_run_key || '-' || i, demo_shelf_id, 'AVAILABLE', CURRENT_DATE - 60, 50000, 'GOOD')
            RETURNING id INTO demo_copy_id;
        INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
            VALUES ('DEMO-S3043-' || demo_run_key || '-' || i, demo_reader_id, demo_staff_id, demo_borrowed) RETURNING id INTO demo_loan_id;
        INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
            VALUES (demo_loan_id, demo_copy_id, demo_borrowed, demo_returned + INTERVAL '1 day');
        UPDATE loan_items SET returned_at = demo_returned WHERE loan_items.loan_id = demo_loan_id;
        IF i = 1 THEN demo_partial_loan_id := demo_loan_id; END IF;
    END LOOP;
    INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
        VALUES (demo_book_id, 'DEMO-S3043-' || demo_run_key || '-OPEN', demo_shelf_id, 'AVAILABLE', CURRENT_DATE - 60, 50000, 'GOOD')
        RETURNING id INTO demo_copy_id;
    INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
        VALUES (demo_partial_loan_id, demo_copy_id, demo_borrowed, CURRENT_TIMESTAMP + INTERVAL '7 days');
END;
$$;
COMMIT;
