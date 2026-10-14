-- S4-06.2: share the S4-06.1 outbox and preserve its five-minute rule.
ALTER TABLE ready_pickup_notifications DROP CONSTRAINT ck_pickup_notification_window;
ALTER TABLE ready_pickup_notifications ADD CONSTRAINT ck_pickup_notification_window CHECK (
    (notification_type = 'READY_FOR_PICKUP' AND send_before = event_at + INTERVAL '5 minutes')
    OR (notification_type = 'LAST_DAY_REMINDER' AND pickup_deadline IS NOT NULL
        AND send_before = pickup_deadline AND event_at < send_before)
);
CREATE UNIQUE INDEX uq_last_day_pickup_reminder
    ON ready_pickup_notifications(reservation_id, pickup_deadline)
    WHERE notification_type = 'LAST_DAY_REMINDER';
CREATE INDEX ix_reservation_last_day_pickup
    ON book_reservations(pickup_deadline, id) WHERE status = 'READY_FOR_PICKUP';
