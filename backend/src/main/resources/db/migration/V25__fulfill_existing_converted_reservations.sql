-- S3-01.3: previous slices could leave a successfully converted reservation READY_FOR_PICKUP.
-- Only an explicit source link, matching reader and matching loan item proves conversion.
-- Preserve every other status and do not infer conversion from a copy's current status.
UPDATE book_reservations r
SET status = 'FULFILLED'
WHERE r.status = 'READY_FOR_PICKUP'
  AND EXISTS (
      SELECT 1
      FROM loans l
      JOIN loan_items li ON li.loan_id = l.id
      WHERE l.reservation_id = r.id
        AND l.borrower_user_id = r.reader_id
        AND li.book_copy_id = r.book_copy_id
  );
