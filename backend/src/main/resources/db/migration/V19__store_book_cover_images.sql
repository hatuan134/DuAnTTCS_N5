-- S2-10.1: one original image per book, isolated from catalog list queries.
CREATE TABLE book_cover_images (
    book_id BIGINT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    image_data BYTEA NOT NULL CHECK (octet_length(image_data) BETWEEN 1 AND 3145728)
);
