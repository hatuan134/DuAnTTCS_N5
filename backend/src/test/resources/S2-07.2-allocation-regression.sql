-- Run after V20 on a local test/demo database. Everything is rolled back.
BEGIN;
DO $$
DECLARE
    test_book BIGINT; other_book BIGINT; test_shelf BIGINT; test_reader BIGINT;
    first_copy BIGINT; second_copy BIGINT; chosen BIGINT; reservation_id BIGINT;
    ready_count BIGINT; marker TEXT := 'REG-S2072-' || txid_current();
    denied BOOLEAN;
BEGIN
    SELECT id INTO test_shelf FROM shelves ORDER BY id LIMIT 1;
    SELECT u.id INTO test_reader FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code = 'READER' ORDER BY u.id LIMIT 1;
    IF test_shelf IS NULL OR test_reader IS NULL OR NOT EXISTS (SELECT 1 FROM books) THEN
        RAISE EXCEPTION 'Create one book, one shelf and one READER before this test';
    END IF;
    INSERT INTO books(title, author_id, category_id)
        SELECT marker, author_id, category_id FROM books ORDER BY id LIMIT 1 RETURNING id INTO test_book;
    INSERT INTO books(title, author_id, category_id)
        SELECT marker || '-OTHER', author_id, category_id FROM books ORDER BY id LIMIT 1 RETURNING id INTO other_book;
    INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition,status)
        VALUES(test_book,marker || '-1',test_shelf,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,0,'GOOD','AVAILABLE')
        RETURNING id INTO first_copy;
    INSERT INTO book_copies(book_id,barcode,shelf_id,received_date,cover_price,physical_condition,status)
        VALUES(test_book,marker || '-2',test_shelf,(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,0,'GOOD','AVAILABLE')
        RETURNING id INTO second_copy;

    -- The same selection rule as BookCopyRepository: select exactly one row.
    SELECT c.id INTO chosen FROM book_copies c
    WHERE c.book_id = test_book AND c.status = 'AVAILABLE'
      AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id=c.id AND li.returned_at IS NULL)
    ORDER BY c.id ASC LIMIT 1 FOR UPDATE OF c;
    IF chosen <> first_copy OR chosen IS NULL THEN RAISE EXCEPTION 'Wrong selected copy'; END IF;
    UPDATE book_copies SET status='HELD' WHERE id=chosen;
    INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,pickup_deadline)
        VALUES(test_book,test_reader,chosen,'READY_FOR_PICKUP',clock_timestamp(),clock_timestamp()+INTERVAL '7 days')
        RETURNING id INTO reservation_id;
    SELECT COUNT(*) INTO ready_count FROM book_copies c
        WHERE c.book_id=test_book AND c.status='AVAILABLE'
          AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id=c.id AND li.returned_at IS NULL);
    IF ready_count <> 1
       OR NOT EXISTS (SELECT 1 FROM book_copies WHERE id=first_copy AND status='HELD')
       OR NOT EXISTS (SELECT 1 FROM book_copies WHERE id=second_copy AND status='AVAILABLE') THEN
        RAISE EXCEPTION 'Must hold exactly one copy and reduce available count by one';
    END IF;

    -- A second request selects the other copy, never the copy already held.
    SELECT c.id INTO chosen FROM book_copies c
    WHERE c.book_id=test_book AND c.status='AVAILABLE'
      AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id=c.id AND li.returned_at IS NULL)
    ORDER BY c.id ASC LIMIT 1 FOR UPDATE OF c;
    IF chosen <> second_copy OR chosen IS NULL THEN RAISE EXCEPTION 'Held copy selected again'; END IF;
    UPDATE book_copies SET status='HELD' WHERE id=chosen;
    INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,pickup_deadline)
        VALUES(test_book,test_reader,chosen,'READY_FOR_PICKUP',clock_timestamp(),clock_timestamp()+INTERVAL '7 days');
    SELECT c.id INTO chosen FROM book_copies c
    WHERE c.book_id=test_book AND c.status='AVAILABLE'
      AND NOT EXISTS (SELECT 1 FROM loan_items li WHERE li.book_copy_id=c.id AND li.returned_at IS NULL)
    ORDER BY c.id ASC LIMIT 1 FOR UPDATE OF c;
    IF chosen IS NOT NULL THEN RAISE EXCEPTION 'Should have no available copy'; END IF;
    INSERT INTO book_reservations(book_id,reader_id,status)
        VALUES(test_book,test_reader,'PENDING') RETURNING id INTO reservation_id;
    IF NOT EXISTS (SELECT 1 FROM book_reservations WHERE id=reservation_id
        AND book_copy_id IS NULL AND pickup_deadline IS NULL) THEN
        RAISE EXCEPTION 'Pending reservation must not bind a copy or deadline';
    END IF;

    denied := FALSE;
    BEGIN
        INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,pickup_deadline)
            VALUES(test_book,test_reader,first_copy,'READY_FOR_PICKUP',clock_timestamp(),clock_timestamp()+INTERVAL '7 days');
    EXCEPTION WHEN unique_violation THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'Two active reservations cannot share a copy'; END IF;
    denied := FALSE;
    BEGIN
        INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status,reserved_at,pickup_deadline)
            VALUES(other_book,test_reader,first_copy,'FULFILLED',clock_timestamp(),clock_timestamp()+INTERVAL '7 days');
    EXCEPTION WHEN foreign_key_violation THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'Allocated copy must belong to the same book'; END IF;
    denied := FALSE;
    BEGIN
        INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status)
            VALUES(test_book,test_reader,first_copy,'PENDING');
    EXCEPTION WHEN check_violation THEN denied := TRUE; END;
    IF NOT denied THEN RAISE EXCEPTION 'Cannot bind a copy to a waiting reservation'; END IF;

    -- A failed reservation write must roll back its preceding copy status update.
    denied := FALSE;
    BEGIN
        UPDATE book_copies SET status='AVAILABLE' WHERE id=first_copy;
        INSERT INTO book_reservations(book_id,reader_id,book_copy_id,status)
            VALUES(test_book,test_reader,first_copy,'READY_FOR_PICKUP');
    EXCEPTION WHEN check_violation THEN denied := TRUE; END;
    IF NOT denied OR NOT EXISTS (SELECT 1 FROM book_copies WHERE id=first_copy AND status='HELD') THEN
        RAISE EXCEPTION 'Allocation write must be atomic';
    END IF;
    RAISE NOTICE 'PASS: one-copy allocation, availability decrease, waiting, copy uniqueness, same-book FK, atomic rollback';
END $$;
ROLLBACK;
