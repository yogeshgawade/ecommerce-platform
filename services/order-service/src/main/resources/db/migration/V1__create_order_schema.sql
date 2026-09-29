CREATE TABLE purchase_order (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL,
    payment_method_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT purchase_order_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE INDEX purchase_order_user_created_idx ON purchase_order(user_id, created_at DESC);

CREATE TABLE purchase_order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_order(order_id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(19, 2) NOT NULL CHECK (line_total > 0),
    CONSTRAINT purchase_order_item_product_unique UNIQUE (order_id, product_id)
);

CREATE INDEX purchase_order_item_order_idx ON purchase_order_item(order_id);

CREATE TABLE order_outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX order_outbox_ready_idx ON order_outbox(next_attempt_at, created_at, id)
    WHERE published_at IS NULL;
