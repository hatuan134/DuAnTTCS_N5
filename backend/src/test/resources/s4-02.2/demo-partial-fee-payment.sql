-- Optional: run ONLY on a disposable demo database migrated through V42.
-- Creates a dedicated reader; never alters existing readers or erases fees.
-- Re-running does not recharge a previously paid demo reader.
BEGIN;
DO $$
DECLARE reader_id BIGINT; type_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
        WHERE u.status = 'ACTIVE' AND r.code = 'LIBRARIAN') THEN
        RAISE EXCEPTION 'Cần có tài khoản Thủ thư ACTIVE trước khi tạo dữ liệu demo.';
    END IF;
    SELECT id INTO reader_id FROM users WHERE email = 's4022-demo@example.invalid';
    IF reader_id IS NOT NULL THEN
        RAISE NOTICE 'Bạn đọc demo đã tồn tại; không tạo thêm nợ. Xem lịch sử biên lai hoặc dùng DB demo mới.';
        RETURN;
    END IF;
    INSERT INTO users(role_id,full_name,email,status)
        SELECT id,'Bạn đọc Demo Thu Một Phần','s4022-demo@example.invalid','ACTIVE' FROM roles WHERE code = 'READER'
        RETURNING id INTO reader_id;
    INSERT INTO reader_profiles(user_id,date_of_birth,member_code,registration_status)
        VALUES(reader_id,'2000-01-01','BD-S4022-DEMO','APPROVED');
    INSERT INTO card_types(name,max_books,loan_days,max_renewals,renewal_days)
        VALUES('Thẻ demo S4-02.2',3,14,1,7) RETURNING id INTO type_id;
    INSERT INTO library_cards(card_number,user_id,card_type_id,expires_at,status)
        VALUES('THE-S4022-DEMO',reader_id,type_id,CURRENT_DATE + 365,'ACTIVE');
    INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES
        (reader_id,'Khoản phí demo 1',10000),
        (reader_id,'Khoản phí demo 2',15000),
        (reader_id,'Khoản phí demo 3',5000);
END;
$$;
COMMIT;
SELECT u.full_name, c.card_number, f.id AS fee_id, f.amount_vnd, f.paid_amount_vnd,
       f.amount_vnd - f.paid_amount_vnd - f.waived_amount_vnd AS outstanding_vnd
FROM users u JOIN reader_fees f ON f.reader_user_id = u.id
LEFT JOIN library_cards c ON c.user_id = u.id
WHERE u.email = 's4022-demo@example.invalid' ORDER BY f.id;
