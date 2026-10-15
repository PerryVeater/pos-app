-- V19: make order attribution mandatory.
-- The legacy unauthenticated /api/v1/orders management API has been removed, so
-- the POS order lifecycle is now the only way an order is created. Every order
-- therefore carries a store and employee derived from the authenticated bearer
-- session, and the columns added as nullable in V18 become NOT NULL so the
-- database enforces the relationship the application already guarantees.
--
-- Forward-only and non-destructive to attributed history: any pre-existing row
-- without attribution could only have come from the removed management path, so
-- it is dropped before the columns are tightened. Children are removed first to
-- respect the foreign keys that reference orders (fk_payment_order and
-- fk_order_line_order), and every delete is restricted to orders missing
-- attribution, so attributed orders and unrelated payments are never touched.
-- On a freshly migrated database the deletes match no rows and the constraint
-- changes are a no-op against the empty table.

DELETE FROM payments
    WHERE order_id IN (
        SELECT id FROM orders WHERE store_id IS NULL OR employee_id IS NULL
    );

DELETE FROM order_line
    WHERE order_id IN (
        SELECT id FROM orders WHERE store_id IS NULL OR employee_id IS NULL
    );

DELETE FROM orders
    WHERE store_id IS NULL OR employee_id IS NULL;

ALTER TABLE orders
    ALTER COLUMN store_id SET NOT NULL,
    ALTER COLUMN employee_id SET NOT NULL;
