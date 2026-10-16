-- S4-05.1: Kịch bản nạp dữ liệu mẫu demo nhắc nhở đến hạn
-- Tạo bạn đọc với 3 cuốn sách mượn: 2 cuốn còn đúng 2 ngày tới hạn và 1 cuốn đến hạn hôm nay
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    reader_user_id BIGINT;
    staff_user_id BIGINT;
    book1_id BIGINT;
    book2_id BIGINT;
    book3_id BIGINT;
    copy1_id BIGINT;
    copy2_id BIGINT;
    copy3_id BIGINT;
    loan_id BIGINT;
    today_vn DATE := (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;
    due_in_2 TIMESTAMPTZ := (today_vn + INTERVAL '2 day' + INTERVAL '17 hour') AT TIME ZONE 'Asia/Ho_Chi_Minh';
    due_today TIMESTAMPTZ := (today_vn + INTERVAL '17 hour') AT TIME ZONE 'Asia/Ho_Chi_Minh';
BEGIN
    -- Tìm nhân viên thực hiện mượn
    SELECT u.id INTO staff_user_id FROM users u 
    JOIN roles r ON r.id = u.role_id 
    WHERE r.code IN ('LIBRARIAN', 'ADMIN') AND u.status = 'ACTIVE' LIMIT 1;

    -- Tìm hoặc tạo bạn đọc demo
    SELECT u.id INTO reader_user_id FROM users u
    JOIN roles r ON r.id = u.role_id
    WHERE r.code = 'READER' AND u.status = 'ACTIVE' LIMIT 1;

    IF reader_user_id IS NULL OR staff_user_id IS NULL THEN
        RAISE EXCEPTION 'Cần có ít nhất 1 bạn đọc và 1 nhân viên thư viện để tạo dữ liệu demo.';
    END IF;

    -- Lấy 3 bản sao sẵn sàng để tạo phiếu mượn
    SELECT id, book_id INTO copy1_id, book1_id FROM book_copies WHERE status = 'AVAILABLE' LIMIT 1;
    SELECT id, book_id INTO copy2_id, book2_id FROM book_copies WHERE status = 'AVAILABLE' AND id <> copy1_id LIMIT 1;
    SELECT id, book_id INTO copy3_id, book3_id FROM book_copies WHERE status = 'AVAILABLE' AND id NOT IN (copy1_id, copy2_id) LIMIT 1;

    IF copy1_id IS NULL OR copy2_id IS NULL OR copy3_id IS NULL THEN
        RAISE NOTICE 'Không đủ 3 bản sao AVAILABLE sẵn có, vui lòng bổ sung bản sao trước khi chạy demo.';
        RETURN;
    END IF;

    -- Tạo phiếu mượn cho bạn đọc
    INSERT INTO loans (loan_number, borrower_user_id, created_by, borrowed_at)
    VALUES ('DEMO-PM-' || floor(random() * 89999 + 10000)::text, reader_user_id, staff_user_id, clock_timestamp())
    RETURNING id INTO loan_id;

    -- Cuốn 1: Còn 2 ngày tới hạn
    INSERT INTO loan_items (loan_id, book_copy_id, borrowed_at, due_date)
    VALUES (loan_id, copy1_id, clock_timestamp(), due_in_2);

    -- Cuốn 2: Còn 2 ngày tới hạn
    INSERT INTO loan_items (loan_id, book_copy_id, borrowed_at, due_date)
    VALUES (loan_id, copy2_id, clock_timestamp(), due_in_2);

    -- Cuốn 3: Đến hạn hôm nay
    INSERT INTO loan_items (loan_id, book_copy_id, borrowed_at, due_date)
    VALUES (loan_id, copy3_id, clock_timestamp(), due_today);

    RAISE NOTICE 'Đã tạo thành công phiếu mượn demo (ID: %) cho bạn đọc ID: % với 3 cuốn sách:', loan_id, reader_user_id;
    RAISE NOTICE '- Cuốn 1 (Bản sao %): Hạn trả % (Còn 2 ngày)', copy1_id, due_in_2;
    RAISE NOTICE '- Cuốn 2 (Bản sao %): Hạn trả % (Còn 2 ngày)', copy2_id, due_in_2;
    RAISE NOTICE '- Cuốn 3 (Bản sao %): Hạn trả % (Đến hạn hôm nay)', copy3_id, due_today;
END;
$$;

COMMIT;
