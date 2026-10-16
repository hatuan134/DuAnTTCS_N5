-- S1-06: bảo đảm mật khẩu hiện tại của các tài khoản đã tồn tại
-- có mặt trong lịch sử để kiểm tra quy tắc 3 mật khẩu gần nhất.
INSERT INTO password_history (user_id, password_hash, created_at)
SELECT u.id,
       u.password_hash,
       COALESCE(u.updated_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.password_hash IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM password_history ph
      WHERE ph.user_id = u.id
        AND ph.password_hash = u.password_hash
  );
