-- TEST DATA ONLY. Run with psql on a disposable/local demo database.
-- Parameters: reader_id, staff_id, copy_id, fixture_label.
-- This is not a migration or a lending API. Keep all IDs from the same test DB.
-- Example fixture_label: demo1. Use a fresh label for each new loan of this copy.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('s2074_demo.reader_id', :'reader_id', true);
SELECT set_config('s2074_demo.staff_id', :'staff_id', true);
SELECT set_config('s2074_demo.copy_id', :'copy_id', true);
SELECT set_config('s2074_demo.fixture_label', :'fixture_label', true);

DO $$
DECLARE
    demo_reader_id BIGINT := current_setting('s2074_demo.reader_id')::BIGINT;
    demo_staff_id BIGINT := current_setting('s2074_demo.staff_id')::BIGINT;
    demo_copy_id BIGINT := current_setting('s2074_demo.copy_id')::BIGINT;
    demo_fixture_label TEXT := current_setting('s2074_demo.fixture_label');
    demo_copy_state TEXT;
    demo_loan_id BIGINT;
    demo_borrowed_time TIMESTAMPTZ := clock_timestamp();
    demo_number TEXT;
BEGIN
    IF demo_fixture_label !~ '^[A-Za-z0-9_-]{1,40}$' THEN
        RAISE EXCEPTION 'fixture_label phải dài 1–40 ký tự chữ, số, gạch ngang hoặc gạch dưới.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
                   WHERE u.id = demo_reader_id AND u.status = 'ACTIVE' AND r.code = 'READER') THEN
        RAISE EXCEPTION 'reader_id phải là Bạn đọc ACTIVE trong database test.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
                   WHERE u.id = demo_staff_id AND u.status = 'ACTIVE'
                     AND r.code IN ('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')) THEN
        RAISE EXCEPTION 'staff_id phải là nhân viên ACTIVE trong database test.';
    END IF;
    SELECT c.status INTO demo_copy_state FROM book_copies c WHERE c.id = demo_copy_id FOR UPDATE;
    IF demo_copy_state IS DISTINCT FROM 'AVAILABLE' THEN
        RAISE EXCEPTION 'copy_id phải tồn tại và đang AVAILABLE. Không sửa tay HELD/BORROWED thành AVAILABLE.';
    END IF;
    IF EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id = demo_copy_id AND li.returned_at IS NULL) THEN
        RAISE EXCEPTION 'Bản này đã có chi tiết mượn chưa trả.';
    END IF;
    demo_number := 'S2074-DEMO-' || demo_fixture_label || '-' || demo_copy_id;
    INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
    VALUES (demo_number, demo_reader_id, demo_staff_id, demo_borrowed_time) RETURNING id INTO demo_loan_id;
    INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
    VALUES (demo_loan_id, demo_copy_id, demo_borrowed_time, demo_borrowed_time + INTERVAL '14 days');
    -- Existing V13 trigger atomically switches AVAILABLE -> BORROWED.
    RAISE NOTICE 'Đã tạo fixture phiếu %, copy %, reader %.', demo_number, demo_copy_id, demo_reader_id;
END;
$$;

SELECT l.loan_number, l.borrower_user_id, li.id AS loan_item_id,
       bc.id AS copy_id, bc.barcode, bc.book_id, b.title, bc.status, li.returned_at
FROM loans l JOIN loan_items li ON li.loan_id = l.id
JOIN book_copies bc ON bc.id = li.book_copy_id JOIN books b ON b.id = bc.book_id
WHERE l.loan_number = 'S2074-DEMO-' || current_setting('s2074_demo.fixture_label')
                      || '-' || current_setting('s2074_demo.copy_id');
COMMIT;
