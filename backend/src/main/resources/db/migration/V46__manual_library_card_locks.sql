-- Manual card restriction is independent of account status and card validity.
-- Expiry is evaluated on every read/write; no scheduled job is required.
ALTER TABLE library_cards
    ADD COLUMN manual_lock_reason VARCHAR(1000),
    ADD COLUMN manual_lock_started_at TIMESTAMPTZ,
    ADD COLUMN manual_lock_ends_at TIMESTAMPTZ,
    ADD COLUMN manual_lock_actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_manual_card_lock CHECK (
        (manual_lock_started_at IS NULL AND manual_lock_reason IS NULL
            AND manual_lock_ends_at IS NULL AND manual_lock_actor_id IS NULL)
        OR (manual_lock_started_at IS NOT NULL AND manual_lock_reason IS NOT NULL
            AND length(btrim(manual_lock_reason)) > 0
            AND (manual_lock_ends_at IS NULL OR manual_lock_ends_at > manual_lock_started_at))
    );
