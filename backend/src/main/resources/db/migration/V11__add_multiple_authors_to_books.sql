-- =========================================================
-- V11__add_multiple_authors_to_books.sql
-- S2-01.2: Gán nhiều tác giả cho một đầu sách
-- Giữ books.author_id làm tác giả chính tương thích ngược,
-- đồng thời dùng book_authors để lưu đầy đủ quan hệ nhiều-nhiều.
-- =========================================================

CREATE TABLE IF NOT EXISTS book_authors (
    book_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_book_authors PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_book_authors_book
        FOREIGN KEY (book_id)
        REFERENCES books(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author
        FOREIGN KEY (author_id)
        REFERENCES authors(id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_book_authors_author_id
    ON book_authors(author_id);

-- Backfill toàn bộ dữ liệu cũ: mỗi đầu sách hiện tại có author_id sẽ có
-- ít nhất một bản ghi tương ứng trong bảng nối.
INSERT INTO book_authors (book_id, author_id)
SELECT id, author_id
FROM books
WHERE author_id IS NOT NULL
ON CONFLICT (book_id, author_id) DO NOTHING;
