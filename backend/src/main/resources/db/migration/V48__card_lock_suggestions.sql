-- S4-04.3: detection never locks a card; the manager must accept explicitly.
CREATE TABLE card_lock_suggestion_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    debt_threshold_vnd BIGINT NOT NULL DEFAULT 100000 CHECK (debt_threshold_vnd >= 0 AND debt_threshold_vnd <= 1000000000000),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_checked_at TIMESTAMPTZ
);
INSERT INTO card_lock_suggestion_settings(id) VALUES (1);
CREATE TABLE card_lock_suggestions (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES library_cards(id),
    card_number VARCHAR(100) NOT NULL,
    reader_name VARCHAR(255) NOT NULL,
    max_overdue_days BIGINT NOT NULL CHECK (max_overdue_days >= 0),
    unpaid_fees_vnd NUMERIC(30,0) NOT NULL CHECK (unpaid_fees_vnd >= 0),
    debt_threshold_vnd BIGINT NOT NULL CHECK (debt_threshold_vnd >= 0),
    reason_code VARCHAR(30) NOT NULL CHECK (reason_code IN ('OVERDUE', 'DEBT', 'BOTH')),
    evidence JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','IGNORED','RESOLVED','CANCELLED_LOCKED')),
    episode_open BOOLEAN NOT NULL DEFAULT TRUE,
    detected_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    decided_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    decider_name VARCHAR(255),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(1000),
    card_status_after_decision VARCHAR(30),
    lock_ends_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    CHECK ((status IN ('ACCEPTED','IGNORED') AND decided_at IS NOT NULL
        AND decider_name IS NOT NULL AND length(btrim(decision_reason)) > 0
        AND card_status_after_decision IS NOT NULL)
        OR (status NOT IN ('ACCEPTED','IGNORED') AND decided_at IS NULL)),
    CHECK (status <> 'PENDING' OR episode_open),
    CHECK (lock_ends_at IS NULL OR (status = 'ACCEPTED' AND lock_ends_at > decided_at))
);
-- An ignored episode suppresses repeated suggestions until recovery.
-- Accepted/cancelled lock cycles close when the corresponding card lock is no longer active.
CREATE UNIQUE INDEX uq_card_lock_open_episode ON card_lock_suggestions(card_id) WHERE episode_open;
CREATE UNIQUE INDEX uq_card_lock_pending ON card_lock_suggestions(card_id) WHERE status = 'PENDING';
CREATE INDEX ix_card_lock_suggestion_history ON card_lock_suggestions(detected_at DESC, id DESC);
