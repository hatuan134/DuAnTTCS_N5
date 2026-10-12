-- OPTIONAL demo data, only on a disposable database already migrated through V39.
-- Uses existing active accounts; does not create passwords or change real fees.
-- Creates three new immutable return histories and three PENDING proposals.
BEGIN;
DO $$
DECLARE
    staff_id BIGINT; reader_id BIGINT; book_id BIGINT; copy_id BIGINT;
    loan_id BIGINT; item_id BIGINT; fee_id BIGINT; request_id BIGINT;
    author_id BIGINT; category_id BIGINT; shelf_id BIGINT;
    label TEXT; tag TEXT; reduction BIGINT;
    demo_returned_at TIMESTAMPTZ := clock_timestamp();
    demo_due_at TIMESTAMPTZ := demo_returned_at - INTERVAL '3 days';
    demo_borrowed_at TIMESTAMPTZ := demo_returned_at - INTERVAL '8 days';
BEGIN
    SELECT u.id INTO staff_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code = 'LIBRARIAN' AND u.status = 'ACTIVE' ORDER BY u.id LIMIT 1;
    SELECT u.id INTO reader_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code = 'READER' AND u.status = 'ACTIVE' ORDER BY u.id LIMIT 1;
    SELECT MIN(id) INTO author_id FROM authors;
    SELECT MIN(id) INTO category_id FROM categories;
    SELECT MIN(id) INTO shelf_id FROM shelves;
    IF staff_id IS NULL OR reader_id IS NULL OR author_id IS NULL OR category_id IS NULL OR shelf_id IS NULL
        OR NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
            WHERE r.code = 'LIBRARY_MANAGER' AND u.status = 'ACTIVE') THEN
        RAISE EXCEPTION 'Cần tài khoản ACTIVE: Thủ thư, Bạn đọc, Quản lý thư viện; cùng tác giả, thể loại và kệ trước khi tạo demo.';
    END IF;
    FOREACH label IN ARRAY ARRAY['PARTIAL', 'FULL', 'REJECT'] LOOP
        tag := 'S4013-DEMO-' || label || '-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISS')
            || '-' || substr(md5(random()::text), 1, 6);
        reduction := CASE WHEN label = 'FULL' THEN 15000 ELSE 10000 END;
        INSERT INTO books(title, author_id, category_id) VALUES ('Demo miễn giảm ' || label, author_id, category_id)
            RETURNING id INTO book_id;
        INSERT INTO book_copies(book_id, barcode, shelf_id, status, received_date, cover_price, physical_condition)
            VALUES (book_id, tag, shelf_id, 'AVAILABLE', CURRENT_DATE - 10, 50000, 'GOOD') RETURNING id INTO copy_id;
        INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
            VALUES (tag, reader_id, staff_id, demo_borrowed_at) RETURNING id INTO loan_id;
        INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
            VALUES (loan_id, copy_id, demo_borrowed_at, demo_due_at) RETURNING id INTO item_id;
        UPDATE loan_items SET returned_at = demo_returned_at, returned_by = staff_id,
            returned_by_name = (SELECT full_name FROM users WHERE id = staff_id) WHERE id = item_id;
        INSERT INTO reader_fees(reader_user_id, description, amount_vnd, return_item_id)
            VALUES (reader_id, 'Demo S4-01.3 ' || label, 15000, item_id) RETURNING id INTO fee_id;
        INSERT INTO return_fee_assessments(item_id, reader_fee_id, snapshot) VALUES (item_id, fee_id,
            jsonb_build_object('status', 'READY', 'message', 'Dữ liệu demo S4-01.3',
                'policyVersion', 'S4-01.1-OPEN-DAYS-HALF-UP-V1', 'dueAt', demo_due_at, 'returnedAt', demo_returned_at,
                'overdueDays', 3, 'dailyRateVnd', 5000, 'rawAmountVnd', 15000, 'amountVnd', 15000,
                'feeId', fee_id, 'excludedDates', '[]'::jsonb, 'weeklySchedule', '[]'::jsonb, 'closedDates', '[]'::jsonb));
        INSERT INTO fee_reduction_requests(reader_fee_id, requested_by, requester_name, original_amount_vnd,
                requested_amount_vnd, reason) VALUES (fee_id, staff_id, 'Tên do trigger ghi nhận', 15000, reduction,
                'Đề nghị demo ' || label || ': bạn đọc có hoàn cảnh khó khăn') RETURNING id INTO request_id;
        RAISE NOTICE '%: phiếu %, khoản phí %, đề nghị %', label, loan_id, fee_id, request_id;
    END LOOP;
END;
$$;
COMMIT;

-- Open /overdue-fees/<fee_id> or use the manager's pending queue.
SELECT r.id AS request_id, r.reader_fee_id AS fee_id, l.id AS loan_id, l.loan_number,
       r.requester_name, r.original_amount_vnd, r.requested_amount_vnd, r.status
FROM fee_reduction_requests r JOIN reader_fees f ON f.id = r.reader_fee_id
JOIN loan_items li ON li.id = f.return_item_id JOIN loans l ON l.id = li.loan_id
WHERE l.loan_number LIKE 'S4013-DEMO-%'
ORDER BY r.id DESC LIMIT 3;
