-- Run on a local/test database AFTER Flyway V7. All test data is rolled back.
BEGIN;
DO $$
DECLARE
    book_a BIGINT;
    book_b BIGINT;
    shelf_a BIGINT;
    copy_a BIGINT;
    marker TEXT := 'S2-02.1-TEST-' || txid_current();
    condition_value TEXT;
    inserted_count INTEGER;
BEGIN
    SELECT MIN(id), MAX(id) INTO book_a, book_b FROM books;
    SELECT MIN(id) INTO shelf_a FROM shelves;
    IF book_a IS NULL OR shelf_a IS NULL OR book_a = book_b THEN
        RAISE EXCEPTION 'Test cần ít nhất hai đầu sách và một kệ.';
    END IF;
    INSERT INTO book_copies(book_id, barcode, shelf_id, received_date, cover_price, physical_condition)
    VALUES(book_a, marker, shelf_a, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 85000, 'GOOD')
    RETURNING id INTO copy_a;
    IF NOT EXISTS (SELECT 1 FROM book_copies WHERE id = copy_a AND book_id = book_a
        AND status = 'AVAILABLE' AND cover_price = 85000 AND physical_condition = 'GOOD') THEN
        RAISE EXCEPTION 'FAIL: thông tin hoặc trạng thái mặc định không đúng';
    END IF;
    INSERT INTO book_copies(book_id, barcode, shelf_id, received_date, cover_price, physical_condition)
    VALUES(book_b, marker, shelf_a, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 0, 'NEW')
    ON CONFLICT (barcode) DO NOTHING;
    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    IF inserted_count <> 0 THEN RAISE EXCEPTION 'FAIL: cho phép mã vạch trùng'; END IF;

    BEGIN
        UPDATE book_copies SET book_id = book_b WHERE id = copy_a;
        RAISE EXCEPTION 'FAIL: cho phép chuyển đầu sách';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    UPDATE book_copies SET book_id = book_a WHERE id = copy_a;

    FOREACH condition_value IN ARRAY ARRAY['NEW','GOOD','OLD','LIGHTLY_DAMAGED','HEAVILY_DAMAGED'] LOOP
        INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition)
        VALUES(book_a,marker || '-' || condition_value,shelf_a,
            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,0,condition_value);
    END LOOP;
    BEGIN
        INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition)
        VALUES(book_a,marker || '-NEGATIVE',shelf_a,
            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,-1,'NEW');
        RAISE EXCEPTION 'FAIL: cho phép giá âm';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition)
        VALUES(book_a,marker || '-FUTURE',shelf_a,
            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 1,0,'NEW');
        RAISE EXCEPTION 'FAIL: cho phép ngày nhập tương lai';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition)
        VALUES(book_a,marker || '-UNKNOWN',shelf_a,
            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,0,'UNKNOWN');
        RAISE EXCEPTION 'FAIL: cho phép tình trạng ngoài danh sách';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO book_copies(book_id,barcode,shelf_id) VALUES(book_a,marker || '-MISSING',shelf_a);
        RAISE EXCEPTION 'FAIL: cho phép bản sao mới thiếu thông tin';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    RAISE NOTICE 'PASS S2-02.1: thông tin, trạng thái, mã trùng, giá, ngày, tình trạng và khóa đầu sách.';
END $$;
ROLLBACK;
