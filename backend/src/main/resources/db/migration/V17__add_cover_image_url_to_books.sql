-- =========================================================
-- V17__add_cover_image_url_to_books.sql
-- S2-06.1: Xem thông tin đầu sách và tình trạng sẵn có
-- Bổ sung trường đường dẫn ảnh bìa (cover_image_url) cho đầu sách.
-- =========================================================

ALTER TABLE books
    ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(1000);
