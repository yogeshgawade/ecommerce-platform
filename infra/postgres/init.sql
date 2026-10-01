-- The PostgreSQL image runs this file only when its data directory is empty.
-- Keep these names in sync with the ECOMMERCE_POSTGRES_*_DB values in .env.
CREATE DATABASE catalog_db;
CREATE DATABASE auth_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
