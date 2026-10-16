-- =========================================================
-- V10__extend_books_for_s2_01_1_basic_catalog_profile.sql
-- S2-01.1: Tạo hồ sơ đầu sách cơ bản
-- Bổ sung nhan đề phụ và số trang, giữ tương thích dữ liệu cũ.
-- =========================================================

ALTER TABLE books
    ADD COLUMN IF NOT EXISTS subtitle VARCHAR(255);

ALTER TABLE books
    ADD COLUMN IF NOT EXISTS page_count INTEGER;

ALTER TABLE books
    DROP CONSTRAINT IF EXISTS ck_books_page_count_positive;

ALTER TABLE books
    ADD CONSTRAINT ck_books_page_count_positive
        CHECK (page_count IS NULL OR page_count > 0);

ALTER TABLE books
    DROP CONSTRAINT IF EXISTS ck_books_publication_year_positive;

ALTER TABLE books
    ADD CONSTRAINT ck_books_publication_year_positive
        CHECK (publication_year IS NULL OR publication_year > 0);
