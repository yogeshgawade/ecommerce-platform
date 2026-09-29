ALTER TABLE inventory_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX inventory_outbox_ready_idx
    ON inventory_outbox(next_attempt_at, created_at, id)
    WHERE published_at IS NULL;
