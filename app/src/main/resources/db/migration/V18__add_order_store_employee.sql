-- V18: store/employee attribution for orders.
-- The POS order lifecycle (POST/GET/PATCH under /api/v1/pos/orders) records
-- which store and employee an order belongs to, derived exclusively from the
-- authenticated bearer session - the client can never choose these identities.
--   * Both columns are NULLABLE because orders created before this feature
--     (and orders still created through the legacy /api/v1/orders management
--     endpoints) have no attribution, and historical ownership must not be
--     inferred or backfilled. POS endpoints treat an unattributed order as
--     non-existent, so the null case never leaks through the new API.
--   * The foreign keys are plain RESTRICT (no CASCADE): an order is a
--     business record, not ephemeral session data, so removing a store or
--     employee that owns orders must be blocked rather than silently delete
--     the order history.
-- No explicit indexes: the referenced columns' PRIMARY KEYs already back the
-- join/ownership lookups, and orders is filtered by store alongside its own
-- small result set rather than being scanned by a dedicated query today.

ALTER TABLE orders
    ADD COLUMN store_id BIGINT,
    ADD COLUMN employee_id BIGINT,
    ADD CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores(id),
    ADD CONSTRAINT fk_orders_employee FOREIGN KEY (employee_id) REFERENCES employee(id);
