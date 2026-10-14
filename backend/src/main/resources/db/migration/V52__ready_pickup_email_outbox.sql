-- S4-06.1: durable events for ALL JPA/JDBC READY transitions; no retroactive emails.
CREATE TABLE ready_pickup_notifications (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES book_reservations(id) ON DELETE RESTRICT,
    notification_type VARCHAR(40) NOT NULL DEFAULT 'READY_FOR_PICKUP',
    reader_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recipient_email VARCHAR(255),
    reader_name VARCHAR(150) NOT NULL,
    book_title TEXT NOT NULL,
    pickup_deadline TIMESTAMPTZ,
    pickup_location TEXT,
    event_at TIMESTAMPTZ NOT NULL,
    send_before TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    subject TEXT NOT NULL DEFAULT '',
    email_content TEXT NOT NULL DEFAULT '',
    error_code VARCHAR(50),
    error_message TEXT,
    CONSTRAINT ck_pickup_notification_status CHECK (status IN ('PENDING','RETRY','SENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT ck_pickup_notification_window CHECK (send_before = event_at + INTERVAL '5 minutes'),
    CONSTRAINT ck_pickup_notification_sent CHECK ((status <> 'SENT' OR sent_at IS NOT NULL) AND (sent_at IS NULL OR status = 'SENT' OR error_code = 'SMTP_ACCEPTED_LATE')),
    CONSTRAINT ck_pickup_notification_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ix_pickup_notification_due ON ready_pickup_notifications(next_attempt_at, id)
    WHERE status IN ('PENDING','RETRY');
CREATE INDEX ix_pickup_notification_reservation ON ready_pickup_notifications(reservation_id, id DESC);
CREATE TABLE ready_pickup_notification_attempts (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES ready_pickup_notifications(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('STARTED','SENT','RETRY','FAILED','SKIPPED')),
    error_code VARCHAR(50),
    error_message TEXT,
    simulated BOOLEAN NOT NULL DEFAULT FALSE,
    actor_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    UNIQUE(notification_id, attempt_number)
);
CREATE FUNCTION enqueue_ready_pickup_notification() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE occurred_at TIMESTAMPTZ;
BEGIN
    IF NEW.status <> 'READY_FOR_PICKUP' THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'READY_FOR_PICKUP' THEN RETURN NEW; END IF;
    END IF;
    occurred_at := clock_timestamp();
    INSERT INTO ready_pickup_notifications (
        reservation_id, reader_id, recipient_email, reader_name, book_title,
        pickup_deadline, event_at, send_before, next_attempt_at)
    SELECT NEW.id, u.id, u.email, u.full_name, b.title,
        NEW.pickup_deadline, occurred_at, occurred_at + INTERVAL '5 minutes', occurred_at
    FROM users u CROSS JOIN books b WHERE u.id = NEW.reader_id AND b.id = NEW.book_id;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_ready_pickup_notification AFTER INSERT OR UPDATE OF status
    ON book_reservations FOR EACH ROW EXECUTE FUNCTION enqueue_ready_pickup_notification();
