-- S4-06.3: cancellation events share the existing pickup email outbox.
-- No backfill: already cancelled orders must not unexpectedly receive email.
ALTER TABLE ready_pickup_notifications
    ADD COLUMN book_id BIGINT REFERENCES books(id) ON DELETE RESTRICT,
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE ready_pickup_notifications ALTER COLUMN send_before DROP NOT NULL;
ALTER TABLE ready_pickup_notifications DROP CONSTRAINT ck_pickup_notification_window;
ALTER TABLE ready_pickup_notifications ADD CONSTRAINT ck_pickup_notification_window CHECK (
    (notification_type = 'READY_FOR_PICKUP' AND send_before IS NOT NULL
        AND send_before = event_at + INTERVAL '5 minutes')
    OR (notification_type = 'LAST_DAY_REMINDER' AND pickup_deadline IS NOT NULL
        AND send_before IS NOT NULL AND send_before = pickup_deadline AND event_at < send_before)
    OR (notification_type = 'PICKUP_EXPIRED_CANCELLATION' AND send_before IS NULL
        AND book_id IS NOT NULL AND cancellation_reason = 'Đã huỷ do quá hạn nhận'
        AND cancelled_at IS NOT NULL AND pickup_deadline IS NOT NULL
        AND cancelled_at > pickup_deadline)
);
CREATE UNIQUE INDEX uq_pickup_expiry_cancellation_event
    ON ready_pickup_notifications(reservation_id, cancelled_at)
    WHERE notification_type = 'PICKUP_EXPIRED_CANCELLATION';

CREATE FUNCTION enqueue_pickup_expiry_cancellation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE occurred_at TIMESTAMPTZ := clock_timestamp();
BEGIN
    IF OLD.status <> 'READY_FOR_PICKUP' OR NEW.status <> 'CANCELLED'
        OR NEW.cancelled_by IS NOT NULL
        OR NEW.cancelled_by_name IS DISTINCT FROM 'Hệ thống'
        OR NEW.cancellation_reason IS DISTINCT FROM 'Đã huỷ do quá hạn nhận'
        OR NEW.pickup_deadline IS NULL OR NEW.cancelled_at IS NULL
        OR NEW.cancelled_at <= NEW.pickup_deadline
        OR occurred_at <= NEW.pickup_deadline
        OR EXISTS(SELECT 1 FROM loans WHERE reservation_id = NEW.id)
        OR EXISTS(SELECT 1 FROM loan_items WHERE source_reservation_id = NEW.id)
    THEN RETURN NEW; END IF;
    INSERT INTO ready_pickup_notifications (
        reservation_id, notification_type, reader_id, recipient_email, reader_name,
        book_id, book_title, pickup_deadline, cancellation_reason, cancelled_at,
        event_at, send_before, next_attempt_at)
    SELECT NEW.id, 'PICKUP_EXPIRED_CANCELLATION', u.id, u.email, u.full_name,
        b.id, b.title, NEW.pickup_deadline, NEW.cancellation_reason, NEW.cancelled_at,
        occurred_at, NULL, occurred_at
    FROM users u CROSS JOIN books b WHERE u.id = NEW.reader_id AND b.id = NEW.book_id
    ON CONFLICT (reservation_id, cancelled_at)
        WHERE notification_type = 'PICKUP_EXPIRED_CANCELLATION' DO NOTHING;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_pickup_expiry_cancellation_email AFTER UPDATE OF status
    ON book_reservations FOR EACH ROW EXECUTE FUNCTION enqueue_pickup_expiry_cancellation();
