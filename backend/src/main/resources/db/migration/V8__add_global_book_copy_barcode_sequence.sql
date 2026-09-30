-- S2-02.2: Dãy mã vạch tự sinh dùng chung toàn hệ thống.
-- Cấu trúc đã chốt cho lát này: tiền tố TV-, 6 chữ số, bắt đầu từ 1.
-- Ví dụ: TV-000001, TV-000002, ...
-- Mã nhập tay vẫn dùng chung cột book_copies.barcode và UNIQUE constraint hiện có.
-- Service sẽ bỏ qua một số trong dãy nếu mã tương ứng đã tồn tại do nhập tay trước đó.
CREATE SEQUENCE book_copy_barcode_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
