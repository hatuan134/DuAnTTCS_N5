-- S2-10.2: thumbnails of uploaded covers; originals remain unchanged.
-- Nullable for existing images; the service backfills on the first thumbnail request.
ALTER TABLE book_cover_images ADD COLUMN thumbnail_data BYTEA;
ALTER TABLE book_cover_images ADD CONSTRAINT ck_book_cover_thumbnail_size
    CHECK (thumbnail_data IS NULL OR octet_length(thumbnail_data) BETWEEN 1 AND 3145728);
