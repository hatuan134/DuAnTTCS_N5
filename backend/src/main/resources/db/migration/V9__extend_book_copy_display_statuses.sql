-- S2-02.3: Cho phép lưu đầy đủ các trạng thái cần hiển thị trên bảng bản sao.
-- Giữ lại LOST và DAMAGED để tương thích với dữ liệu/trạng thái cũ đã có từ S1-09.
ALTER TABLE book_copies
    DROP CONSTRAINT ck_book_copies_status;

ALTER TABLE book_copies
    ADD CONSTRAINT ck_book_copies_status
        CHECK (status IN (
            'AVAILABLE',
            'BORROWED',
            'HELD',
            'REPAIR',
            'REMOVED',
            'LOST',
            'DAMAGED'
        ));
