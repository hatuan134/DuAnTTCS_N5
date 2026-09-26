-- V2__add_card_type_details.sql
-- S1-05: Khai bao cac loai the kem chinh sach muon

ALTER TABLE card_types
ADD COLUMN IF NOT EXISTS description VARCHAR(500),
ADD COLUMN IF NOT EXISTS duration_months INTEGER NOT NULL DEFAULT 12;

-- Khoi tao du lieu mau neu chua co loai the nao
INSERT INTO card_types (name, description, duration_months, max_books, loan_days, max_renewals, renewal_days, is_active)
SELECT 'Thẻ sinh viên', 'Dành cho sinh viên đang học tại trường.', 12, 5, 14, 2, 7, TRUE
WHERE NOT EXISTS (SELECT 1 FROM card_types WHERE LOWER(name) = 'thẻ sinh viên');

INSERT INTO card_types (name, description, duration_months, max_books, loan_days, max_renewals, renewal_days, is_active)
SELECT 'Thẻ cán bộ', 'Dành cho cán bộ, giảng viên và nhân viên.', 24, 10, 30, 3, 14, TRUE
WHERE NOT EXISTS (SELECT 1 FROM card_types WHERE LOWER(name) = 'thẻ cán bộ');
