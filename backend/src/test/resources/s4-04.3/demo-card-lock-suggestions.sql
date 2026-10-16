-- Run ONLY against a disposable local demo database, after V48.
-- psql parameters: card_a, card_b, copy_a, staff_id. Requires two existing eligible cards and an AVAILABLE copy.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE s4043_demo_parameters ON COMMIT DROP AS
SELECT :'card_a'::text AS card_a, :'card_b'::text AS card_b,
       :'copy_a'::text AS copy_a, :'staff_id'::bigint AS staff_id;
DO $$
DECLARE
    p RECORD; a RECORD; b RECORD; copy_id BIGINT; loan_id BIGINT;
    today DATE := (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;
    due_day DATE; due_at TIMESTAMPTZ; borrowed_at TIMESTAMPTZ; open_count INTEGER;
BEGIN
    SELECT * INTO STRICT p FROM s4043_demo_parameters;
    SELECT c.*, u.status AS account_status, u.locked_until AS account_locked_until INTO STRICT a FROM library_cards c JOIN users u ON u.id=c.user_id WHERE c.card_number=p.card_a;
    SELECT c.*, u.status AS account_status, u.locked_until AS account_locked_until INTO STRICT b FROM library_cards c JOIN users u ON u.id=c.user_id WHERE c.card_number=p.card_b;
    IF a.id=b.id THEN RAISE EXCEPTION 'Chọn hai thẻ khác nhau.'; END IF;
    PERFORM pg_advisory_xact_lock(-LEAST(a.user_id,b.user_id));
    PERFORM pg_advisory_xact_lock(-GREATEST(a.user_id,b.user_id));
    IF a.status<>'ACTIVE' OR b.status<>'ACTIVE' OR a.account_status<>'ACTIVE' OR b.account_status<>'ACTIVE'
        OR a.expires_at<today OR b.expires_at<today
        OR a.account_locked_until>clock_timestamp() OR b.account_locked_until>clock_timestamp()
        OR (a.manual_lock_released_at IS NULL AND a.manual_lock_started_at <= clock_timestamp()
            AND (a.manual_lock_ends_at IS NULL OR a.manual_lock_ends_at>clock_timestamp()))
        OR (b.manual_lock_released_at IS NULL AND b.manual_lock_started_at <= clock_timestamp()
            AND (b.manual_lock_ends_at IS NULL OR b.manual_lock_ends_at>clock_timestamp())) THEN
        RAISE EXCEPTION 'Cần hai thẻ còn hiệu lực và chưa bị khóa.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=p.staff_id
        AND u.status='ACTIVE' AND r.code IN ('LIBRARIAN','LIBRARY_MANAGER','ADMIN')) THEN
        RAISE EXCEPTION 'staff_id phải là tài khoản nhân viên đang hoạt động.';
    END IF;
    IF EXISTS (SELECT 1 FROM card_lock_suggestions WHERE card_id IN (a.id,b.id) AND episode_open) THEN
        RAISE EXCEPTION 'Hai thẻ đang có đợt đề xuất. Hãy dùng hai thẻ khác cho demo.';
    END IF;
    IF EXISTS (SELECT 1 FROM reader_fees f WHERE f.reader_user_id IN (a.user_id,b.user_id)
        AND f.amount_vnd>f.paid_amount_vnd+f.waived_amount_vnd) THEN
        RAISE EXCEPTION 'Hai bạn đọc phải hết nợ trước khi tạo dữ liệu demo.';
    END IF;
    IF EXISTS (SELECT 1 FROM loan_items li JOIN loans l ON l.id=li.loan_id
        WHERE l.borrower_user_id IN (a.user_id,b.user_id) AND li.returned_at IS NULL
        AND li.due_date < (today::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')) THEN
        RAISE EXCEPTION 'Hai bạn đọc không được có phiếu quá hạn trước khi demo.';
    END IF;
    IF (SELECT count(*) FROM library_weekly_schedule)<>7 THEN
        RAISE EXCEPTION 'Cấu hình lịch thư viện đủ 7 thứ trước khi demo.';
    END IF;
    SELECT count(*),min(d) INTO open_count,due_day FROM (
        SELECT date_value::date AS d FROM generate_series(today-3650,today,interval '1 day') date_value
        JOIN library_weekly_schedule w ON w.day_of_week=extract(isodow FROM date_value)::int AND w.is_open
        WHERE NOT EXISTS (SELECT 1 FROM library_closed_dates c WHERE c.closed_date=date_value::date)
        ORDER BY date_value DESC LIMIT 36
    ) open_days;
    IF open_count<>36 THEN RAISE EXCEPTION 'Lịch thư viện không có đủ 36 ngày mở cửa trong khoảng chuẩn bị demo.'; END IF;
    SELECT (due_day+close_time) AT TIME ZONE 'Asia/Ho_Chi_Minh' INTO due_at
        FROM library_weekly_schedule WHERE day_of_week=extract(isodow FROM due_day)::int;
    IF due_at IS NULL THEN RAISE EXCEPTION 'Thiếu giờ đóng cửa của ngày hạn trả.'; END IF;
    borrowed_at := ((due_day-1)+time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh';
    SELECT id INTO STRICT copy_id FROM book_copies WHERE barcode=p.copy_a AND status='AVAILABLE' FOR UPDATE;
    INSERT INTO loans(loan_number,borrower_user_id,created_by,borrowed_at)
        VALUES ('S4043-DEMO-'||to_char(clock_timestamp(),'YYYYMMDDHH24MISSUS'),a.user_id,p.staff_id,borrowed_at)
        RETURNING id INTO loan_id;
    INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date)
        VALUES (loan_id,copy_id,borrowed_at,due_at);
    INSERT INTO reader_fees(reader_user_id,description,amount_vnd)
        VALUES (b.user_id,'S4-04.3 DEMO - khoản phải thu khác',150000);
    RAISE NOTICE 'Đã tạo: A quá hạn 35 ngày mở cửa (hạn %), B nợ 150000 VND. Mở Đề xuất khóa thẻ và bấm Làm mới.',due_day;
END;
$$;
COMMIT;
