CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL UNIQUE,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    last_updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inventory_reserved_within_quantity CHECK (reserved_quantity <= quantity)
);

CREATE TABLE inventory_reservation (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory_reservation_line (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL REFERENCES inventory_reservation(order_id),
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT inventory_reservation_line_order_product_unique UNIQUE (order_id, product_id)
);

CREATE INDEX inventory_reservation_line_order_idx ON inventory_reservation_line(order_id);

CREATE TABLE inventory_outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX inventory_outbox_unpublished_idx ON inventory_outbox(created_at, id)
    WHERE published_at IS NULL;