CREATE TABLE products (
    id           VARCHAR(36) PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    description  VARCHAR(5000),
    category     VARCHAR(100) NOT NULL,
    brand        VARCHAR(100) NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE product_attributes (
    product_id      VARCHAR(36) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    attribute_name  VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(500),
    PRIMARY KEY (product_id, attribute_name)
);

CREATE TABLE product_outbox (
    id           VARCHAR(36) PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    message_key  VARCHAR(200) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER NOT NULL DEFAULT 0,
    last_error   VARCHAR(2000)
);

CREATE INDEX idx_product_outbox_pending
    ON product_outbox (published_at, created_at, id);
