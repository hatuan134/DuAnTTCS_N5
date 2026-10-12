-- Remove ONLY the synthetic, unreturned data loaded by demo-overdue-readers.sql.
-- Run via psql with ON_ERROR_STOP=1. Stop the backend first; keep PostgreSQL running.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(7400101);

LOCK TABLE users, reader_profiles, library_cards, card_types, books, book_copies,
    loans, loan_items, book_reservations, book_copy_status_history,
    reader_fees, return_fee_assessments, overdue_loan_contacts,
    loan_rejections, loan_rejection_reasons, book_authors
    IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE cleanup_expected_readers ON COMMIT DROP AS
SELECT n, 'demo.overdue.' || n || '@libra.example.invalid' AS email,
       'DEMO-LIBRA-FIX-01-' || n || '-TV' AS card_number,
       'DEMO-LIBRA-FIX-01-' || n || '-PM' AS loan_number
FROM generate_series(1, 3) AS g(n);

CREATE TEMP TABLE cleanup_expected_copies ON COMMIT DROP AS
SELECT n, k, 'DEMO-LIBRA-FIX-01-' || n || '-COPY-' || k AS barcode,
       'Sách demo quá hạn ' || n || '.' || k || ' — LIBRA-FIX-01' AS title
FROM generate_series(1, 3) AS g(n)
CROSS JOIN LATERAL generate_series(1, n) AS i(k);

CREATE TEMP TABLE cleanup_readers ON COMMIT DROP AS
SELECT u.id, e.n, e.email, e.card_number, e.loan_number
FROM users u JOIN cleanup_expected_readers e ON lower(u.email) = e.email;
CREATE TEMP TABLE cleanup_loans ON COMMIT DROP AS
SELECT l.id, l.borrower_user_id, e.n
FROM loans l JOIN cleanup_expected_readers e ON l.loan_number = e.loan_number;
CREATE TEMP TABLE cleanup_copies ON COMMIT DROP AS
SELECT c.id, c.book_id, e.n, e.k, e.title
FROM book_copies c JOIN cleanup_expected_copies e ON c.barcode = e.barcode;
CREATE TEMP TABLE cleanup_books ON COMMIT DROP AS
SELECT DISTINCT book_id AS id FROM cleanup_copies;

-- The new one-reader version borrows an EXISTING copy. Never add it to cleanup_books.
CREATE TEMP TABLE cleanup_single_reader ON COMMIT DROP AS
SELECT id FROM users WHERE lower(email) = 'demo.overdue.single@libra.example.invalid';
CREATE TEMP TABLE cleanup_single_loan ON COMMIT DROP AS
SELECT id FROM loans WHERE loan_number = 'DEMO-LIBRA-FIX-01-SINGLE-PM';

DO $$
DECLARE
    single_reader_id BIGINT; voucher_id BIGINT; existing_copy_id BIGINT; existing_book_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cleanup_single_reader) AND NOT EXISTS (SELECT 1 FROM cleanup_single_loan) THEN
        RETURN;
    END IF;
    IF (SELECT COUNT(*) FROM cleanup_single_reader) <> 1 OR (SELECT COUNT(*) FROM cleanup_single_loan) <> 1 THEN
        RAISE EXCEPTION 'Demo một bạn đọc không đủ dấu nhận diện. Chưa xóa gì.';
    END IF;
    SELECT id INTO single_reader_id FROM cleanup_single_reader;
    SELECT id INTO voucher_id FROM cleanup_single_loan;
    IF NOT EXISTS (SELECT 1 FROM loans l JOIN users u ON u.id = l.borrower_user_id
        JOIN roles r ON r.id = u.role_id JOIN library_cards c ON c.user_id = u.id
        JOIN card_types t ON t.id = c.card_type_id
        WHERE l.id = voucher_id AND u.id = single_reader_id AND r.code = 'READER'
          AND u.password_hash IS NULL AND u.full_name = 'Bạn đọc demo quá hạn'
          AND u.address = 'Dữ liệu demo LIBRA-FIX-01 SINGLE'
          AND c.card_number = 'DEMO-LIBRA-FIX-01-SINGLE-TV' AND t.name = 'DEMO LIBRA-FIX-01'
          AND l.reservation_id IS NULL AND l.direct_request_id IS NULL)
       OR (SELECT COUNT(*) FROM loan_items WHERE loan_id = voucher_id) <> 1
       OR EXISTS (SELECT 1 FROM loan_items WHERE loan_id = voucher_id
                  AND (returned_at IS NOT NULL OR source_reservation_id IS NOT NULL)) THEN
        RAISE EXCEPTION 'Demo một bạn đọc đã thay đổi hoặc đã ghi nhận trả sách. Chưa xóa gì.';
    END IF;
    SELECT li.book_copy_id, c.book_id INTO existing_copy_id, existing_book_id
    FROM loan_items li JOIN book_copies c ON c.id = li.book_copy_id
    WHERE li.loan_id = voucher_id AND c.status = 'BORROWED';
    IF existing_copy_id IS NULL
       OR EXISTS (SELECT 1 FROM loans WHERE borrower_user_id = single_reader_id AND id <> voucher_id)
       OR EXISTS (SELECT 1 FROM library_cards WHERE user_id = single_reader_id AND card_number <> 'DEMO-LIBRA-FIX-01-SINGLE-TV')
       OR EXISTS (SELECT 1 FROM loan_items WHERE book_copy_id = existing_copy_id AND returned_at IS NULL AND loan_id <> voucher_id)
    THEN
        RAISE EXCEPTION 'Demo một bạn đọc có dữ liệu mượn/thẻ/bản sao khác. Chưa xóa gì.';
    END IF;
    IF EXISTS (SELECT 1 FROM book_reservations r WHERE r.reader_id = single_reader_id
               OR (r.book_id = existing_book_id AND r.status IN ('PENDING', 'READY_FOR_PICKUP')))
       OR EXISTS (SELECT 1 FROM reader_fees f WHERE f.reader_user_id = single_reader_id)
       OR EXISTS (SELECT 1 FROM return_fee_assessments a JOIN loan_items li ON li.id = a.item_id WHERE li.loan_id = voucher_id) THEN
        RAISE EXCEPTION 'Sách/bạn đọc demo có đặt giữ hoặc phí mới. Chưa xóa gì; không tự xóa giao dịch khác.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'loan_items'::regclass
                   AND tgname = 'trg_guard_loan_item_copy' AND tgenabled = 'O') THEN
        RAISE EXCEPTION 'Trigger bảo vệ phiếu mượn không ở trạng thái dự kiến. Chưa xóa gì.';
    END IF;
    DELETE FROM overdue_loan_contacts WHERE loan_id = voucher_id;
    DELETE FROM loan_rejection_reasons WHERE rejection_id IN (
        SELECT j.id FROM loan_rejections j WHERE j.reader_user_id = single_reader_id AND card_number = 'DEMO-LIBRA-FIX-01-SINGLE-TV');
    DELETE FROM loan_rejections j WHERE j.reader_user_id = single_reader_id AND card_number = 'DEMO-LIBRA-FIX-01-SINGLE-TV';
    EXECUTE 'ALTER TABLE loan_items DISABLE TRIGGER trg_guard_loan_item_copy';
    DELETE FROM loan_items WHERE loan_id = voucher_id;
    EXECUTE 'ALTER TABLE loan_items ENABLE TRIGGER trg_guard_loan_item_copy';
    DELETE FROM loans WHERE id = voucher_id;
    -- Restore the original copy; preserve its identity, book and every earlier loan/history.
    UPDATE book_copies SET status = 'AVAILABLE' WHERE id = existing_copy_id;
    DELETE FROM password_reset_requests WHERE user_id = single_reader_id
       OR lower(requested_email) = 'demo.overdue.single@libra.example.invalid';
    DELETE FROM users WHERE id = single_reader_id;
    RAISE NOTICE 'Đã xóa 1 bạn đọc/1 phiếu demo; giữ nguyên sách và khôi phục bản sao % về Sẵn sàng.', existing_copy_id;
END;
$$;

DO $$
DECLARE
    e RECORD; reader_id BIGINT; voucher_id BIGINT; count_items BIGINT;
    deleted_items BIGINT; deleted_loans BIGINT; deleted_readers BIGINT;
BEGIN
    -- A normal seed run is atomic. A fully cleaned database is a valid no-op.
    FOR e IN SELECT * FROM cleanup_expected_readers ORDER BY n LOOP
        SELECT id INTO reader_id FROM cleanup_readers WHERE n = e.n;
        SELECT id INTO voucher_id FROM cleanup_loans WHERE n = e.n;
        SELECT COUNT(*) INTO count_items FROM cleanup_copies WHERE n = e.n;
        IF reader_id IS NULL AND voucher_id IS NULL AND count_items = 0 THEN
            CONTINUE;
        END IF;
        IF reader_id IS NULL OR voucher_id IS NULL OR count_items <> e.n THEN
            RAISE EXCEPTION 'Dữ liệu demo nhóm % không đủ dấu nhận diện. Chưa xóa gì; cần đối chiếu lại.', e.n;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
            JOIN library_cards c ON c.user_id = u.id
            JOIN card_types t ON t.id = c.card_type_id
            JOIN loans l ON l.id = voucher_id
            WHERE u.id = reader_id AND r.code = 'READER' AND u.password_hash IS NULL
              AND u.full_name = 'Bạn đọc demo quá hạn ' || e.n
              AND u.address = 'Dữ liệu demo LIBRA-FIX-01'
              AND c.card_number = e.card_number AND t.name = 'DEMO LIBRA-FIX-01'
              AND l.borrower_user_id = u.id AND l.reservation_id IS NULL
              AND l.direct_request_id IS NULL
        ) THEN
            RAISE EXCEPTION 'Tài khoản/thẻ/phiếu demo nhóm % đã thay đổi hoặc không khớp. Chưa xóa gì.', e.n;
        END IF;
        IF (SELECT COUNT(*) FROM loan_items WHERE loan_id = voucher_id) <> e.n
           OR EXISTS (
               SELECT 1 FROM loan_items li
               WHERE li.loan_id = voucher_id AND (
                   li.returned_at IS NOT NULL OR li.source_reservation_id IS NOT NULL
                   OR NOT EXISTS (SELECT 1 FROM cleanup_copies c WHERE c.n = e.n AND c.id = li.book_copy_id)
               )
           ) THEN
            RAISE EXCEPTION 'Phiếu demo nhóm % đã trả sách hoặc có dòng khác dữ liệu nạp. Chưa xóa gì.', e.n;
        END IF;
    END LOOP;

    IF EXISTS (SELECT 1 FROM cleanup_copies c JOIN books b ON b.id = c.book_id
               JOIN book_copies real_copy ON real_copy.book_id = b.id
               WHERE b.title <> c.title OR real_copy.id <> c.id OR real_copy.status <> 'BORROWED')
       OR EXISTS (SELECT 1 FROM loan_items li JOIN cleanup_copies c ON c.id = li.book_copy_id
                  WHERE NOT EXISTS (SELECT 1 FROM cleanup_loans l WHERE l.id = li.loan_id))
       OR EXISTS (SELECT 1 FROM loans l JOIN cleanup_readers r ON r.id = l.borrower_user_id
                  WHERE NOT EXISTS (SELECT 1 FROM cleanup_loans old WHERE old.id = l.id))
       OR EXISTS (SELECT 1 FROM library_cards c JOIN cleanup_readers r ON r.id = c.user_id
                  WHERE c.card_number <> r.card_number) THEN
        RAISE EXCEPTION 'Dữ liệu demo đang gắn với bản sao/phiếu/thẻ khác. Chưa xóa gì.';
    END IF;

    IF EXISTS (SELECT 1 FROM book_reservations r
               WHERE r.reader_id IN (SELECT id FROM cleanup_readers)
                  OR r.book_id IN (SELECT id FROM cleanup_books))
       OR EXISTS (SELECT 1 FROM book_copy_status_history h
                  WHERE h.book_copy_id IN (SELECT id FROM cleanup_copies))
       OR EXISTS (SELECT 1 FROM reader_fees f WHERE f.reader_user_id IN (SELECT id FROM cleanup_readers))
       OR EXISTS (SELECT 1 FROM return_fee_assessments a JOIN loan_items li ON li.id = a.item_id
                  WHERE li.loan_id IN (SELECT id FROM cleanup_loans)) THEN
        RAISE EXCEPTION 'Sách/bạn đọc demo đã phát sinh đặt giữ, lịch sử sửa sách hoặc phí trả sách. Chưa xóa gì; cần dọn theo dữ liệu thực tế.';
    END IF;

    -- Linked synthetic contact/rejection snapshots can exist after checking demo cards.
    DELETE FROM overdue_loan_contacts WHERE loan_id IN (SELECT id FROM cleanup_loans);
    DELETE FROM loan_rejection_reasons WHERE rejection_id IN (
        SELECT j.id FROM loan_rejections j JOIN cleanup_readers r
          ON r.id = j.reader_user_id AND r.card_number = j.card_number
    );
    DELETE FROM loan_rejections j USING cleanup_readers r
      WHERE r.id = j.reader_user_id AND r.card_number = j.card_number;

    IF EXISTS (SELECT 1 FROM cleanup_loans) THEN
        IF NOT EXISTS (SELECT 1 FROM pg_trigger
                       WHERE tgrelid = 'loan_items'::regclass
                         AND tgname = 'trg_guard_loan_item_copy' AND tgenabled = 'O') THEN
            RAISE EXCEPTION 'Trigger bảo vệ phiếu mượn không ở trạng thái dự kiến. Toàn bộ thao tác rollback.';
        END IF;
        -- The source forbids all item deletion, even synthetic rows. Suspend ONLY this
        -- named user trigger while the table is locked, then restore in this transaction.
        EXECUTE 'ALTER TABLE loan_items DISABLE TRIGGER trg_guard_loan_item_copy';
        DELETE FROM loan_items WHERE loan_id IN (SELECT id FROM cleanup_loans);
        GET DIAGNOSTICS deleted_items = ROW_COUNT;
        EXECUTE 'ALTER TABLE loan_items ENABLE TRIGGER trg_guard_loan_item_copy';
    ELSE
        deleted_items := 0;
    END IF;

    DELETE FROM loans WHERE id IN (SELECT id FROM cleanup_loans);
    GET DIAGNOSTICS deleted_loans = ROW_COUNT;
    DELETE FROM book_copies WHERE id IN (SELECT id FROM cleanup_copies);
    DELETE FROM books WHERE id IN (SELECT id FROM cleanup_books);
    DELETE FROM password_reset_requests WHERE user_id IN (SELECT id FROM cleanup_readers)
       OR lower(requested_email) IN (SELECT email FROM cleanup_readers);
    DELETE FROM users WHERE id IN (SELECT id FROM cleanup_readers);
    GET DIAGNOSTICS deleted_readers = ROW_COUNT;
    -- Reader profiles, cards, auth tokens and password history use their existing CASCADE.
    DELETE FROM card_types t WHERE t.name = 'DEMO LIBRA-FIX-01'
      AND NOT EXISTS (SELECT 1 FROM library_cards c WHERE c.card_type_id = t.id);
    RAISE NOTICE 'Đã xóa % bạn đọc demo, % phiếu demo và % dòng sách demo.', deleted_readers, deleted_loans, deleted_items;
END;
$$;
COMMIT;

-- Both counts should be 0 after successful cleanup; Flyway and normal data stay intact.
SELECT
    (SELECT COUNT(*) FROM users WHERE lower(email) IN (
      'demo.overdue.1@libra.example.invalid', 'demo.overdue.2@libra.example.invalid', 'demo.overdue.3@libra.example.invalid', 'demo.overdue.single@libra.example.invalid')) AS remaining_demo_readers,
    (SELECT COUNT(*) FROM loans WHERE loan_number IN (
      'DEMO-LIBRA-FIX-01-1-PM', 'DEMO-LIBRA-FIX-01-2-PM', 'DEMO-LIBRA-FIX-01-3-PM', 'DEMO-LIBRA-FIX-01-SINGLE-PM')) AS remaining_demo_loans;
