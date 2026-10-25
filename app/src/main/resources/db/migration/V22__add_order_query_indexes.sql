-- V22: indexes for the order, order-line and payment read paths (D4).
-- PostgreSQL indexes primary keys and UNIQUE constraints and nothing else, so
-- fk_orders_store, fk_orders_employee, fk_payment_order, fk_order_line_order
-- and fk_order_line_product all created no index on their referencing column.
-- Every store-scoped read below therefore had to scan the whole table, and so
-- did every parent delete, because a RESTRICT foreign key runs a lookup on the
-- child table before it allows one. V18 documented that omission; this fixes
-- it. Each index is justified by a query, not by the existence of a key.
--
--   * idx_orders_store_created_at serves the store's order history:
--       SELECT ... FROM orders WHERE store_id = ? ORDER BY created_at DESC LIMIT ?
--     which is the listing the POS needs as soon as it stops showing one order
--     at a time. The (store_id, created_at) order lets a B-tree return rows
--     already sorted, so the LIMIT stops the scan after N rows instead of
--     sorting the store's history. It also covers the bare store_id prefix for
--     "does this store own any orders" - the check a store delete runs.
--
--   * idx_orders_employee_id serves the same history per employee (who sold
--     what, for the reporting M3 introduces) and the employee-side RESTRICT
--     check when an employee row is removed.
--
--   * idx_payment_order_id serves order -> payments: what has been paid
--     against this order, which refunds and the reconciliation work in M6
--     both need. Today's path goes payment -> order, which is already a
--     primary-key lookup; this is the direction that had none. It also serves
--     the order-side RESTRICT check that fk_payment_order runs on every order
--     delete.
--
--   * idx_order_line_order_id serves the query Hibernate already issues on
--     every order read: Order.lines is loaded with
--     SELECT ... FROM order_line WHERE order_id = ?.
--     This is the hottest of the five and it ran as a sequential scan.
--
--   * idx_order_line_product_id serves per-item sales: how many of this item
--     did we sell, over what window (M5/M6 reporting), and the product-side
--     RESTRICT check. It is deliberately single-column: the reporting queries
--     filter by item first and join back to the order for time.
--
-- Already covered elsewhere, so nothing is added for them:
--   * employee_store_assignment: uk_employee_store_assignment (employee_id,
--     store_id) leads on employee_id, idx_esa_store_id (V15) covers the
--     store direction.
--   * employee_group_membership: same pattern, idx_egm_employee_group_id (V14).
--   * employee_pin_store_scope: uk_epss_store_fingerprint (store_id,
--     pin_fingerprint) covers the store-side lookup, uk_epss_employee_store
--     (employee_id, store_id) the employee side.
--   * pos_session: V20 added both of the paths the lifecycle uses.
--
-- No data change, but not a free migration either: a plain CREATE INDEX holds
-- a lock that blocks writes to the table while it builds. At the volume this
-- database can hold today that is milliseconds, so forward-only and
-- transactional is the right trade; if a table ever grows past the point where
-- that is true, the fix is a concurrent index build in its own
-- non-transactional migration, not a wider lock.

CREATE INDEX idx_orders_store_created_at ON orders (store_id, created_at);

CREATE INDEX idx_orders_employee_id ON orders (employee_id);

CREATE INDEX idx_payment_order_id ON payments (order_id);

CREATE INDEX idx_order_line_order_id ON order_line (order_id);

CREATE INDEX idx_order_line_product_id ON order_line (product_id);
