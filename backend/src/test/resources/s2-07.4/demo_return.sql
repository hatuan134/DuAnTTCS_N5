-- TEST DATA ONLY. Parameters: reader_id, copy_id, fixture_label.
-- Use the same values as demo_borrow.sql. Never delete loan_items: V13 prohibits it.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('s2074_demo.reader_id', :'reader_id', true);
SELECT set_config('s2074_demo.copy_id', :'copy_id', true);
SELECT set_config('s2074_demo.fixture_label', :'fixture_label', true);

DO $$
DECLARE
    demo_reader_id BIGINT := current_setting('s2074_demo.reader_id')::BIGINT;
    demo_copy_id BIGINT := current_setting('s2074_demo.copy_id')::BIGINT;
    demo_fixture_label TEXT := current_setting('s2074_demo.fixture_label');
    demo_updated_count INTEGER;
BEGIN
    IF demo_fixture_label !~ '^[A-Za-z0-9_-]{1,40}$' THEN
        RAISE EXCEPTION 'fixture_label không hợp lệ.';
    END IF;
    UPDATE loan_items li SET returned_at = clock_timestamp()
    FROM loans l
    WHERE l.id = li.loan_id AND l.borrower_user_id = demo_reader_id
      AND l.loan_number = 'S2074-DEMO-' || demo_fixture_label || '-' || demo_copy_id
      AND li.book_copy_id = demo_copy_id AND li.returned_at IS NULL;
    GET DIAGNOSTICS demo_updated_count = ROW_COUNT;
    IF demo_updated_count <> 1 THEN
        RAISE EXCEPTION 'Không tìm thấy đúng một bản chưa trả của fixture. Kiểm tra IDs/label hoặc bản đã trả.';
    END IF;
    -- Existing V13 trigger switches BORROWED -> AVAILABLE in this transaction.
END;
$$;

SELECT l.loan_number, li.id AS loan_item_id, bc.id AS copy_id, bc.barcode,
       bc.book_id, bc.status, li.returned_at
FROM loans l JOIN loan_items li ON li.loan_id = l.id
JOIN book_copies bc ON bc.id = li.book_copy_id
WHERE l.borrower_user_id = current_setting('s2074_demo.reader_id')::BIGINT
  AND l.loan_number = 'S2074-DEMO-' || current_setting('s2074_demo.fixture_label')
                      || '-' || current_setting('s2074_demo.copy_id');
COMMIT;
