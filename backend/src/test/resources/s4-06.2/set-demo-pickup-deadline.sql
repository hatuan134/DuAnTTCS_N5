-- Acceptance fixture only. Pass both parameters using psql -v.
-- Changes only the chosen READY order; no inserts, deletes or status changes.
\if :{?reservation_id}
\else
\echo 'Missing -v reservation_id=...'
\quit
\endif
\if :{?pickup_deadline}
\else
\echo 'Missing -v pickup_deadline=YYYY-MM-DDTHH:mm:ss+07:00'
\quit
\endif
BEGIN;
SELECT id, status, pickup_deadline AS original_deadline
FROM book_reservations WHERE id = :'reservation_id'::BIGINT FOR UPDATE;
UPDATE book_reservations r SET pickup_deadline = :'pickup_deadline'::TIMESTAMPTZ
WHERE r.id = :'reservation_id'::BIGINT AND r.status = 'READY_FOR_PICKUP'
  AND r.book_copy_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.reservation_id = r.id)
  AND NOT EXISTS (SELECT 1 FROM loan_items i WHERE i.source_reservation_id = r.id)
RETURNING r.id, r.status, r.pickup_deadline;
COMMIT;
