-- Local test after V13 and demo seed. All writes below are rolled back.
BEGIN;
DO $$
DECLARE b BIGINT; s BIGINT; u BIGINT; c BIGINT; l BIGINT; h BIGINT;
    marker TEXT := 'REG-S2032-' || txid_current(); denied BOOLEAN;
BEGIN
    SELECT id INTO b FROM books ORDER BY id LIMIT 1;
    SELECT id INTO s FROM shelves ORDER BY id LIMIT 1;
    SELECT id INTO u FROM users ORDER BY id LIMIT 1;
    IF b IS NULL OR s IS NULL OR u IS NULL THEN RAISE EXCEPTION 'Cần chạy dữ liệu demo trước.'; END IF;
    INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition)
        VALUES(b,marker,s,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,0,'GOOD') RETURNING id INTO c;
    INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES(marker,u,u) RETURNING id INTO l;
    INSERT INTO loan_items(loan_id,book_copy_id) VALUES(l,c);
    IF NOT EXISTS (SELECT 1 FROM book_copies WHERE id=c AND status='BORROWED')
        OR NOT EXISTS (SELECT 1 FROM loan_items WHERE book_copy_id=c AND returned_at IS NULL) THEN
        RAISE EXCEPTION 'FAIL: chưa ghi nhận đúng lượt mượn.';
    END IF;
    UPDATE loan_items SET returned_at=clock_timestamp() WHERE book_copy_id=c;
    IF NOT EXISTS (SELECT 1 FROM book_copies WHERE id=c AND status='AVAILABLE') THEN
        RAISE EXCEPTION 'FAIL: trả sách không khôi phục Sẵn sàng.';
    END IF;
    -- Model the same atomic repair/history writes; deliberately fail history.
    BEGIN
        UPDATE book_copies SET status='REPAIR' WHERE id=c;
        INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,reason)
            VALUES(c,'AVAILABLE','REPAIR',u,'Test',' ');
        RAISE EXCEPTION 'FAIL: chấp nhận lý do trống.';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    IF NOT EXISTS (SELECT 1 FROM book_copies WHERE id=c AND status='AVAILABLE') THEN
        RAISE EXCEPTION 'FAIL: trạng thái không rollback cùng lịch sử.';
    END IF;
    UPDATE book_copies SET status='REPAIR' WHERE id=c;
    INSERT INTO book_copy_status_history(book_copy_id,previous_status,new_status,actor_user_id,actor_name,reason)
        VALUES(c,'AVAILABLE','REPAIR',u,'Test','Bong gáy') RETURNING id INTO h;
    denied := FALSE;
    BEGIN UPDATE book_copy_status_history SET reason='Sửa' WHERE id=h;
    EXCEPTION WHEN raise_exception THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'FAIL: cho sửa lịch sử.'; END IF;
    denied := FALSE;
    BEGIN DELETE FROM book_copy_status_history WHERE id=h;
    EXCEPTION WHEN raise_exception THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'FAIL: cho xóa lịch sử.'; END IF;
    denied := FALSE;
    BEGIN INSERT INTO loan_items(loan_id,book_copy_id) VALUES(l,c);
    EXCEPTION WHEN raise_exception THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'FAIL: cho mượn bản đang sửa chữa.'; END IF;
    RAISE NOTICE 'PASS: nền tảng mượn/trả, rollback lịch sử, chặn sửa/xóa, chặn mượn bản sửa chữa.';
END $$;
ROLLBACK;
