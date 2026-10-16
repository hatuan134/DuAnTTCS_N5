-- S2-07.2: optional allocation, preserving existing PENDING/legacy reservations.
ALTER TABLE book_copies
    ADD CONSTRAINT uq_book_copies_id_book UNIQUE (id, book_id);

ALTER TABLE book_reservations
    ADD COLUMN book_copy_id BIGINT,
    ADD CONSTRAINT fk_reservations_copy_same_book
        FOREIGN KEY (book_copy_id, book_id)
        REFERENCES book_copies(id, book_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_reservations_allocated_copy
        CHECK (book_copy_id IS NULL OR (
            status <> 'PENDING'
            AND pickup_deadline IS NOT NULL
            AND pickup_deadline > reserved_at
        ));

-- One physical copy cannot be allocated to two active pickup reservations.
-- This is not a duplicate-title or per-reader reservation limit.
CREATE UNIQUE INDEX ux_reservations_ready_copy
    ON book_reservations(book_copy_id)
    WHERE book_copy_id IS NOT NULL AND status = 'READY_FOR_PICKUP';
