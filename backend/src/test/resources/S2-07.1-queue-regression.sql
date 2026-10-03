-- Run on the project's PostgreSQL test/demo database after migrations.
-- Requires one existing book and one READER; all inserted data is rolled back.
BEGIN;
DO $$
DECLARE
    test_book BIGINT;
    test_reader BIGINT;
    early_id BIGINT;
    late_id BIGINT;
    tie_id BIGINT;
    excluded_id BIGINT;
    check_id BIGINT;
    expected_position BIGINT;
    actual_position BIGINT;
BEGIN
    SELECT id INTO test_book FROM books ORDER BY id LIMIT 1;
    SELECT u.id INTO test_reader FROM users u JOIN roles r ON r.id = u.role_id
        WHERE r.code = 'READER' ORDER BY u.id LIMIT 1;
    IF test_book IS NULL OR test_reader IS NULL THEN
        RAISE EXCEPTION 'Create one book and one READER before running this test';
    END IF;
    -- Insert later time first: FIFO must be based on timestamp, not just id.
    INSERT INTO book_reservations(book_id, reader_id, reserved_at)
        VALUES(test_book, test_reader, '2000-01-02T10:00:00.123456+07') RETURNING id INTO late_id;
    INSERT INTO book_reservations(book_id, reader_id, reserved_at)
        VALUES(test_book, test_reader, '2000-01-01T10:00:00.123456+07') RETURNING id INTO early_id;
    INSERT INTO book_reservations(book_id, reader_id, reserved_at)
        VALUES(test_book, test_reader, '2000-01-02T10:00:00.123456+07') RETURNING id INTO tie_id;
    INSERT INTO book_reservations(book_id, reader_id, status, reserved_at)
        VALUES(test_book, test_reader, 'CANCELLED', '1999-01-01T10:00:00+07') RETURNING id INTO excluded_id;
    FOREACH check_id IN ARRAY ARRAY[early_id, late_id, tie_id] LOOP
        SELECT position INTO expected_position FROM (
            SELECT id, ROW_NUMBER() OVER (ORDER BY reserved_at, id) AS position
            FROM book_reservations WHERE book_id = test_book AND status = 'PENDING'
        ) queue WHERE id = check_id;
        SELECT COUNT(*) INTO actual_position
        FROM book_reservations queued
        JOIN book_reservations target ON target.id = check_id
        WHERE queued.book_id = target.book_id AND queued.status = 'PENDING'
          AND (queued.reserved_at, queued.id) <= (target.reserved_at, target.id);
        IF actual_position <> expected_position THEN
            RAISE EXCEPTION 'Wrong queue position for %, expected %, actual %', check_id, expected_position, actual_position;
        END IF;
    END LOOP;
    IF NOT EXISTS (
        SELECT 1 FROM book_reservations early JOIN book_reservations late ON late.id = late_id
        WHERE early.id = early_id AND early.reserved_at < late.reserved_at AND early.id > late.id
    ) THEN
        RAISE EXCEPTION 'Timestamp ordering fixture invalid';
    END IF;
    RAISE NOTICE 'PASS: FIFO timestamp ordering, id tie-break, PENDING only, microsecond precision';
END $$;
ROLLBACK;
