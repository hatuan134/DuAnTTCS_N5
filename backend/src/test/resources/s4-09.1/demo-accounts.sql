-- S4-09.1 OPTIONAL MANUAL QA ONLY. Run after Flyway on a disposable database named s4091_*.
-- Four accounts below use the test-only password: ReviewTest123!
-- This file is not a Flyway migration and is never run automatically.
BEGIN;
DO $$ BEGIN
    IF left(current_database(), 6) <> 's4091_' THEN
        RAISE EXCEPTION 'Chỉ chạy dữ liệu thử S4-09.1 trên database riêng có tên bắt đầu s4091_';
    END IF;
END $$;
INSERT INTO users(role_id,full_name,email,password_hash,status)
SELECT r.id, d.full_name, d.email, '$2b$12$CTzh0FW..UlkForpmn4.k.5M4PNnbutOS2.MQNkOWlZTXAyQnIsLq', 'ACTIVE'
FROM (VALUES
    ('ADMIN','S4091 Quản trị','s4091-admin@example.invalid'),
    ('LIBRARY_MANAGER','S4091 Quản lý','s4091-manager@example.invalid'),
    ('LIBRARIAN','S4091 Thủ thư','s4091-librarian@example.invalid'),
    ('READER','S4091 Bạn đọc','s4091-reader@example.invalid')
) d(role_code,full_name,email) JOIN roles r ON r.code=d.role_code
ON CONFLICT ((LOWER(email))) DO NOTHING;
INSERT INTO reader_profiles(user_id,member_code,registration_status)
SELECT id,'S4091-READER','APPROVED' FROM users WHERE email='s4091-reader@example.invalid'
ON CONFLICT (user_id) DO NOTHING;
INSERT INTO library_cards(card_number,user_id,card_type_id,expires_at,status)
SELECT 'S4091-CARD',u.id,(SELECT MIN(id) FROM card_types),CURRENT_DATE+365,'ACTIVE'
FROM users u WHERE email='s4091-reader@example.invalid'
AND NOT EXISTS (SELECT 1 FROM library_cards c WHERE c.user_id=u.id);
INSERT INTO reader_fees(reader_user_id,description,amount_vnd)
SELECT u.id,'S4091 phí thử nghiệm',10000 FROM users u
WHERE email='s4091-reader@example.invalid'
AND NOT EXISTS (SELECT 1 FROM reader_fees f WHERE f.reader_user_id=u.id AND f.description='S4091 phí thử nghiệm');
COMMIT;
