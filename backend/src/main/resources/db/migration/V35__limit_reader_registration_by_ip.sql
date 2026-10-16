-- S1-03: Lưu số lượt gửi đăng ký theo địa chỉ IP trong cửa sổ trượt 60 phút.
-- Không sửa migration cũ hoặc bảng users/reader_profiles.
CREATE TABLE reader_registration_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ip_address VARCHAR(100) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reader_registration_attempts_ip_time
    ON reader_registration_attempts (ip_address, attempted_at DESC);
