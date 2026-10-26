-- V24: business timestamps become instants (D8).
-- orders.created_at and payments.created_at were TIMESTAMP (without time
-- zone), written from LocalDateTime.now() - the JVM's clock in the JVM's
-- zone - while pos_session already stored TIMESTAMPTZ (V17). The same value
-- therefore meant "wall-clock time wherever the server happened to run": a
-- laptop in America/Denver and a container with TZ=UTC wrote times five hours
-- apart that both looked correct, and neither recorded which convention it
-- used. Every business timestamp is now an absolute instant.
--
-- Type on each side. Java: Instant. Database: TIMESTAMPTZ. A zone in the
-- payload would only invite clients to trust it; the store's zone is applied
-- where a zone is actually needed (business days), so stores.timezone stays
-- the one place a zone enters the system.
--
-- Backfill. A naive timestamp cannot become an instant without naming the zone
-- it was read in, and the defensible answer is the store's zone rather than the
-- server's: the value was meant to be local time at the store. So every row is
-- reinterpreted in its own store's zone (payments via order -> store). Rows
-- written by a server already running in UTC shift by their store's offset;
-- that is a correction, not a loss, because the old value never said it was UTC
-- and cannot be distinguished from a local one. A store in the server's zone
-- sees no change at all. Because orders.store_id and payments.order_id are
-- NOT NULL with live foreign keys, the joins below cannot miss a row and
-- cannot leave the new column null.
--
-- The column is rebuilt instead of cast in place because ALTER ... TYPE ...
-- USING cannot read another table. Two consequences of that: the rebuilt column
-- moves to the end of the physical column list (a rename cannot restore the old
-- position, and nothing depends on that order, since JPA and every query name
-- their columns), and dropping the old created_at drops
-- idx_orders_store_created_at (V22) with it, so the index is recreated at the
-- end: a column rewrite must not quietly delete the index that covers it.

ALTER TABLE orders
    ADD COLUMN created_at_utc TIMESTAMPTZ;

UPDATE orders o
   SET created_at_utc = o.created_at AT TIME ZONE s.timezone
  FROM stores s
 WHERE s.id = o.store_id;

ALTER TABLE orders
    ALTER COLUMN created_at_utc SET NOT NULL;

DROP INDEX idx_orders_store_created_at;

ALTER TABLE orders DROP COLUMN created_at;

ALTER TABLE orders RENAME COLUMN created_at_utc TO created_at;

CREATE INDEX idx_orders_store_created_at ON orders (store_id, created_at);

ALTER TABLE payments
    ADD COLUMN created_at_utc TIMESTAMPTZ;

UPDATE payments p
   SET created_at_utc = p.created_at AT TIME ZONE s.timezone
  FROM orders o
  JOIN stores s ON s.id = o.store_id
 WHERE o.id = p.order_id;

ALTER TABLE payments
    ALTER COLUMN created_at_utc SET NOT NULL;

ALTER TABLE payments DROP COLUMN created_at;

ALTER TABLE payments RENAME COLUMN created_at_utc TO created_at;
