-- End a manual restriction without changing account/card base status or its scheduled expiry.
ALTER TABLE library_cards ADD COLUMN manual_lock_released_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_manual_card_release CHECK (manual_lock_released_at IS NULL
        OR (manual_lock_started_at IS NOT NULL AND manual_lock_released_at >= manual_lock_started_at));

CREATE TABLE library_card_lock_history (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES library_cards(id),
    card_number VARCHAR(100) NOT NULL,
    action VARCHAR(10) NOT NULL CHECK (action IN ('LOCK', 'UNLOCK')),
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_name VARCHAR(255) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) > 0),
    lock_started_at TIMESTAMPTZ NOT NULL,
    lock_ends_at TIMESTAMPTZ,
    lock_reason VARCHAR(1000) NOT NULL,
    lock_actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    lock_actor_name VARCHAR(255) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    resulting_status VARCHAR(30) NOT NULL,
    CONSTRAINT uq_card_lock_event UNIQUE (card_id, lock_started_at, action),
    CHECK (lock_ends_at IS NULL OR lock_ends_at > lock_started_at),
    CHECK (performed_at >= lock_started_at)
);
CREATE INDEX ix_card_lock_history ON library_card_lock_history(card_id, performed_at DESC, id DESC);

-- Preserve all earlier S4-04.1 locks, including those overwritten on the card.
-- The old audit events did not snapshot effective before/after status: retain UNKNOWN rather than invent it.
INSERT INTO library_card_lock_history (card_id, card_number, action, actor_user_id, actor_name,
    performed_at, reason, lock_started_at, lock_ends_at, lock_reason, lock_actor_user_id,
    lock_actor_name, previous_status, resulting_status)
SELECT c.id, c.card_number, 'LOCK', a.actor_user_id, COALESCE(u.full_name, 'Không xác định'),
    (a.after_data->>'startedAt')::timestamptz, a.after_data->>'reason',
    (a.after_data->>'startedAt')::timestamptz, (a.after_data->>'endsAt')::timestamptz,
    a.after_data->>'reason', a.actor_user_id, COALESCE(u.full_name, 'Không xác định'), 'UNKNOWN', 'UNKNOWN'
FROM audit_logs a JOIN library_cards c ON a.entity_id = c.id::text
LEFT JOIN users u ON u.id = a.actor_user_id
WHERE a.action = 'LIBRARY_CARD_MANUALLY_LOCKED' AND a.entity_type = 'LIBRARY_CARD'
    AND a.after_data->>'startedAt' IS NOT NULL AND length(btrim(a.after_data->>'reason')) > 0
ORDER BY a.id
ON CONFLICT (card_id, lock_started_at, action) DO NOTHING;

-- Also retain current lock metadata when an old audit row is absent/redacted.
INSERT INTO library_card_lock_history (card_id, card_number, action, actor_user_id, actor_name,
    performed_at, reason, lock_started_at, lock_ends_at, lock_reason, lock_actor_user_id,
    lock_actor_name, previous_status, resulting_status)
SELECT c.id, c.card_number, 'LOCK', c.manual_lock_actor_id, COALESCE(u.full_name, 'Không xác định'),
    c.manual_lock_started_at, c.manual_lock_reason, c.manual_lock_started_at, c.manual_lock_ends_at,
    c.manual_lock_reason, c.manual_lock_actor_id, COALESCE(u.full_name, 'Không xác định'), 'UNKNOWN', 'UNKNOWN'
FROM library_cards c LEFT JOIN users u ON u.id = c.manual_lock_actor_id
WHERE c.manual_lock_started_at IS NOT NULL
ON CONFLICT (card_id, lock_started_at, action) DO NOTHING;
