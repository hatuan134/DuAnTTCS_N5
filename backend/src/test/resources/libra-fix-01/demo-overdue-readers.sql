-- OPTIONAL LOCAL DEMO: ONE reader, ONE overdue loan, ONE EXISTING available copy.
-- Run after V40. No INSERT into books/book_copies; no shared login password.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(7400101);
DO $$
DECLARE
    demo_staff_id BIGINT; demo_reader_role BIGINT; demo_reader_id BIGINT; demo_type_id BIGINT;
    demo_book_id BIGINT; demo_copy_id BIGINT; demo_loan_id BIGINT; member_number BIGINT;
    tag TEXT := 'DEMO-LIBRA-FIX-01-SINGLE';
    email_value TEXT := 'demo.overdue.single@libra.example.invalid';
    member_value TEXT; borrowed_value TIMESTAMPTZ; due_value TIMESTAMPTZ;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'loan_items' AND column_name = 'source_reservation_id') THEN
        RAISE EXCEPTION 'Hãy chạy backend để Flyway hoàn tất V40 trước khi nạp demo.';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE lower(email) IN (
            'demo.overdue.1@libra.example.invalid', 'demo.overdue.2@libra.example.invalid', 'demo.overdue.3@libra.example.invalid'))
       OR EXISTS (SELECT 1 FROM loans WHERE loan_number IN (
            'DEMO-LIBRA-FIX-01-1-PM', 'DEMO-LIBRA-FIX-01-2-PM', 'DEMO-LIBRA-FIX-01-3-PM')) THEN
        RAISE EXCEPTION 'Dữ liệu demo cũ 3 bạn đọc vẫn còn. Chạy Remove-OverdueReaders.ps1 trước, rồi nạp lại bản 1 bạn đọc.';
    END IF;
    SELECT l.id INTO demo_loan_id FROM loans l WHERE l.loan_number = tag || '-PM';
    IF demo_loan_id IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM loans l JOIN users u ON u.id = l.borrower_user_id
                       JOIN library_cards c ON c.user_id = u.id
                       WHERE l.id = demo_loan_id AND lower(u.email) = email_value AND c.card_number = tag || '-TV')
           OR (SELECT COUNT(*) FROM loan_items li WHERE li.loan_id = demo_loan_id) <> 1 THEN
            RAISE EXCEPTION 'Mã phiếu demo đã tồn tại nhưng không khớp dữ liệu mẫu. Không thay đổi dữ liệu này.';
        END IF;
        RAISE NOTICE 'Phiếu demo đã có; giữ nguyên, không tạo thêm bạn đọc/sách/phiếu.';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE lower(email) = email_value) THEN
        RAISE EXCEPTION 'Email demo đã tồn tại nhưng thiếu phiếu demo. Không thay đổi tài khoản này.';
    END IF;
    SELECT u.id INTO demo_staff_id FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code IN ('LIBRARIAN', 'ADMIN') AND u.status = 'ACTIVE'
        ORDER BY CASE r.code WHEN 'LIBRARIAN' THEN 0 ELSE 1 END, u.id LIMIT 1;
    SELECT id INTO demo_reader_role FROM roles WHERE code = 'READER';
    IF demo_staff_id IS NULL OR demo_reader_role IS NULL THEN
        RAISE EXCEPTION 'Cần một Thủ thư hoặc Admin ACTIVE trước khi nạp demo.';
    END IF;
    -- Title -> physical copy lock order matches reservation/return services.
    -- Do not take copies from readers borrowing or waiting for the title.
    SELECT b.id INTO demo_book_id FROM books b
    WHERE EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.id AND c.status = 'AVAILABLE'
                  AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id = c.id AND li.returned_at IS NULL))
      AND NOT EXISTS (SELECT 1 FROM book_reservations r WHERE r.book_id = b.id
                      AND r.status IN ('PENDING', 'READY_FOR_PICKUP'))
    ORDER BY b.id LIMIT 1 FOR UPDATE OF b SKIP LOCKED;
    IF demo_book_id IS NULL THEN
        RAISE EXCEPTION 'Không có sách có bản sao Sẵn sàng và không có hàng đợi đặt giữ. Chưa nạp gì; không tạo sách mới.';
    END IF;
    SELECT c.id INTO demo_copy_id FROM book_copies c
    WHERE c.book_id = demo_book_id AND c.status = 'AVAILABLE'
      AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id = c.id AND li.returned_at IS NULL)
      AND NOT EXISTS (SELECT 1 FROM book_reservations r WHERE r.book_copy_id = c.id AND r.status = 'READY_FOR_PICKUP')
    ORDER BY c.id LIMIT 1 FOR UPDATE OF c SKIP LOCKED;
    IF demo_copy_id IS NULL THEN
        RAISE EXCEPTION 'Bản sao đã thay đổi hoặc đang được xử lý. Chưa nạp gì; vui lòng thử lại.';
    END IF;
    SELECT id INTO demo_type_id FROM card_types WHERE name = 'DEMO LIBRA-FIX-01' ORDER BY id LIMIT 1;
    IF demo_type_id IS NULL THEN
        INSERT INTO card_types(name, max_books, loan_days, max_renewals, renewal_days)
            VALUES ('DEMO LIBRA-FIX-01', 5, 14, 1, 7) RETURNING id INTO demo_type_id;
    END IF;
    INSERT INTO users(role_id, full_name, email, phone, address, status)
        VALUES (demo_reader_role, 'Bạn đọc demo quá hạn', email_value, '0900000001',
                'Dữ liệu demo LIBRA-FIX-01 SINGLE', 'ACTIVE') RETURNING id INTO demo_reader_id;
    LOOP
        member_number := nextval('reader_member_code_seq');
        member_value := 'BD' || lpad(member_number::text, GREATEST(6, length(member_number::text)), '0');
        EXIT WHEN NOT EXISTS (SELECT 1 FROM reader_profiles WHERE lower(member_code) = lower(member_value));
    END LOOP;
    INSERT INTO reader_profiles(user_id, date_of_birth, member_code, registration_status, reviewed_at, reviewed_by)
        VALUES (demo_reader_id, DATE '2005-01-01', member_value, 'APPROVED', clock_timestamp(), demo_staff_id);
    INSERT INTO library_cards(card_number, user_id, card_type_id, issued_at, expires_at, status, created_by)
        VALUES (tag || '-TV', demo_reader_id, demo_type_id, CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', demo_staff_id);
    due_value := (CURRENT_DATE - 3 + TIME '17:00') AT TIME ZONE 'Asia/Ho_Chi_Minh';
    borrowed_value := due_value - INTERVAL '14 days';
    INSERT INTO loans(loan_number, borrower_user_id, created_by, borrowed_at)
        VALUES (tag || '-PM', demo_reader_id, demo_staff_id, borrowed_value) RETURNING id INTO demo_loan_id;
    -- Normal trigger transitions the chosen EXISTING copy to BORROWED.
    INSERT INTO loan_items(loan_id, book_copy_id, borrowed_at, due_date)
        VALUES (demo_loan_id, demo_copy_id, borrowed_value, due_value);
END;
$$;
COMMIT;

SELECT u.full_name, c.card_number, l.loan_number, b.title AS existing_book,
       bc.barcode AS existing_barcode, li.returned_at, li.due_date,
       GREATEST(0, CURRENT_DATE - (li.due_date AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) AS overdue_days
FROM loans l JOIN users u ON u.id = l.borrower_user_id
JOIN library_cards c ON c.user_id = u.id JOIN loan_items li ON li.loan_id = l.id
JOIN book_copies bc ON bc.id = li.book_copy_id JOIN books b ON b.id = bc.book_id
WHERE l.loan_number = 'DEMO-LIBRA-FIX-01-SINGLE-PM';
