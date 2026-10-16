-- =========================================================
-- V3__create_catalog_author_category_and_books.sql
-- S1-08: Khai báo danh mục tác giả và thể loại
-- Hỗ trợ ràng buộc đầu sách, tối đa 2 cấp thể loại
-- =========================================================

-- 1. Bổ sung trường note cho tác giả nếu chưa có
ALTER TABLE authors
ADD COLUMN IF NOT EXISTS note VARCHAR(1000);

-- 2. Bổ sung trường description cho thể loại nếu chưa có
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS description VARCHAR(1000);

-- 3. Điều chỉnh chỉ mục duy nhất cho tên thể loại:
-- Tên trùng nhau trong cùng một danh mục (cùng cấp cha hoặc cùng cấp 1) bị từ chối
DROP INDEX IF EXISTS ux_categories_name_lower;

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_parent_name_lower
    ON categories (COALESCE(parent_id, 0), LOWER(name));

-- 4. Bảng đầu sách (books) để quản lý biên mục sách và kiểm soát liên kết tác giả, thể loại
CREATE TABLE IF NOT EXISTS books (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(50),
    title VARCHAR(255) NOT NULL,
    author_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    publisher VARCHAR(255),
    publication_year INTEGER,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_books_author
        FOREIGN KEY (author_id)
        REFERENCES authors(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_books_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_books_author_id ON books(author_id);
CREATE INDEX IF NOT EXISTS ix_books_category_id ON books(category_id);

DROP TRIGGER IF EXISTS trg_books_updated_at ON books;
CREATE TRIGGER trg_books_updated_at
BEFORE UPDATE ON books
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- 5. Khởi tạo dữ liệu mẫu cho Tác giả
INSERT INTO authors (name, note, is_active)
SELECT 'Nguyễn Nhật Ánh', 'Nhà văn Việt Nam với nhiều tác phẩm tuổi học trò kinh điển.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE LOWER(name) = 'nguyễn nhật ánh');

INSERT INTO authors (name, note, is_active)
SELECT 'Nam Cao', 'Nhà văn hiện thực xuất sắc với nhiều truyện ngắn hiện thực phê phán.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE LOWER(name) = 'nam cao');

INSERT INTO authors (name, note, is_active)
SELECT 'Tô Hoài', 'Tác giả kiệt tác Dế Mèn phiêu lưu ký và nhiều tác phẩm thiếu nhi.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE LOWER(name) = 'tô hoài');

INSERT INTO authors (name, note, is_active)
SELECT 'Vũ Trọng Phụng', 'Tác giả Số đỏ, Giông tố (Đã ngừng sử dụng để kiểm chứng dữ liệu sách cũ).', FALSE
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE LOWER(name) = 'vũ trọng phụng');

INSERT INTO authors (name, note, is_active)
SELECT 'Tác giả thử nghiệm xoá', 'Tác giả chưa gắn với đầu sách nào để kiểm thử tính năng xoá an toàn.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE LOWER(name) = 'tác giả thử nghiệm xoá');

-- 6. Khởi tạo dữ liệu mẫu cho Thể loại (tối đa 2 cấp)
-- Cấp 1
INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (1, 'Văn học', NULL, 'Các tác phẩm văn học trong nước và quốc tế.', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (2, 'Công nghệ thông tin', NULL, 'Sách khoa học máy tính, lập trình và chuyển đổi số.', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (3, 'Kinh tế & Quản trị', NULL, 'Tài liệu quản trị kinh doanh, tài chính và khởi nghiệp.', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (4, 'Thể loại cũ ngừng dùng', NULL, 'Thể loại đã ngừng sử dụng (để kiểm chứng sách cũ vẫn hiện nhưng biên mục mới không chọn được).', FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (5, 'Thể loại thử nghiệm xoá', NULL, 'Thể loại chưa có đầu sách nào để kiểm thử xoá an toàn.', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Cấp 2 (thuộc Văn học - id 1)
INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (6, 'Văn học trong nước', 1, 'Tác phẩm văn học Việt Nam hiện đại và trung đại.', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (7, 'Văn học nước ngoài', 1, 'Tác phẩm văn học dịch nước ngoài kinh điển.', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Cấp 2 (thuộc CNTT - id 2)
INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (8, 'Lập trình Web', 2, 'Kỹ thuật phát triển ứng dụng Web Frontend và Backend.', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, parent_id, description, is_active)
OVERRIDING SYSTEM VALUE
VALUES (9, 'Khoa học dữ liệu & AI', 2, 'Tài liệu Machine Learning, Trí tuệ nhân tạo và Phân tích dữ liệu.', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Cập nhật sequence của bảng categories
SELECT setval('categories_id_seq', (SELECT GREATEST(MAX(id), 10) FROM categories));

-- 7. Khởi tạo dữ liệu mẫu cho Sách (biên mục sách ban đầu)
-- Sách với tác giả/thể loại đang hoạt động
INSERT INTO books (isbn, title, author_id, category_id, publisher, publication_year, description)
SELECT '978-604-2-00001-1', 'Cho tôi xin một vé đi tuổi thơ', a.id, c.id, 'NXB Trẻ', 2008, 'Tác phẩm đoạt giải thưởng Văn học ASEAN năm 2010.'
FROM authors a, categories c
WHERE a.name = 'Nguyễn Nhật Ánh' AND c.name = 'Văn học trong nước'
  AND NOT EXISTS (SELECT 1 FROM books WHERE isbn = '978-604-2-00001-1');

INSERT INTO books (isbn, title, author_id, category_id, publisher, publication_year, description)
SELECT '978-604-2-00002-2', 'Mắt biếc', a.id, c.id, 'NXB Trẻ', 1990, 'Một trong những truyện dài được yêu thích nhất của Nguyễn Nhật Ánh.'
FROM authors a, categories c
WHERE a.name = 'Nguyễn Nhật Ánh' AND c.name = 'Văn học trong nước'
  AND NOT EXISTS (SELECT 1 FROM books WHERE isbn = '978-604-2-00002-2');

INSERT INTO books (isbn, title, author_id, category_id, publisher, publication_year, description)
SELECT '978-604-2-00003-3', 'Chí Phèo', a.id, c.id, 'NXB Văn học', 1941, 'Kiệt tác văn học hiện thực phê phán Việt Nam.'
FROM authors a, categories c
WHERE a.name = 'Nam Cao' AND c.name = 'Văn học trong nước'
  AND NOT EXISTS (SELECT 1 FROM books WHERE isbn = '978-604-2-00003-3');

INSERT INTO books (isbn, title, author_id, category_id, publisher, publication_year, description)
SELECT '978-604-2-00004-4', 'Dế Mèn phiêu lưu ký', a.id, c.id, 'NXB Kim Đồng', 1941, 'Tác phẩm thiếu nhi kinh điển gắn bó với nhiều thế hệ bạn đọc.'
FROM authors a, categories c
WHERE a.name = 'Tô Hoài' AND c.name = 'Văn học trong nước'
  AND NOT EXISTS (SELECT 1 FROM books WHERE isbn = '978-604-2-00004-4');

-- Sách cũ gắn với tác giả đã ngừng sử dụng và thể loại cũ đã ngừng sử dụng:
-- Kiểm chứng: Vẫn hiển thị đầy đủ trên sách cũ, nhưng không xuất hiện trong ô chọn khi biên mục mới
INSERT INTO books (isbn, title, author_id, category_id, publisher, publication_year, description)
SELECT '978-604-2-00005-5', 'Số đỏ (Bản lưu trữ thư viện)', a.id, c.id, 'NXB Văn học', 1938, 'Sách lưu trữ cũ phục vụ nghiên cứu; tác giả và thể loại gốc đã được đánh dấu ngừng sử dụng.'
FROM authors a, categories c
WHERE a.name = 'Vũ Trọng Phụng' AND c.name = 'Thể loại cũ ngừng dùng'
  AND NOT EXISTS (SELECT 1 FROM books WHERE isbn = '978-604-2-00005-5');
