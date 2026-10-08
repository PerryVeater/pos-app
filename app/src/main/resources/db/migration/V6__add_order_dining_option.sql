-- V6: Add dining_option column to orders table
-- Add as nullable first to allow existing rows to be updated
ALTER TABLE orders ADD COLUMN IF NOT EXISTS dining_option VARCHAR(20);

-- Set default value for existing orders
UPDATE orders SET dining_option = 'DINE_IN' WHERE dining_option IS NULL;

-- Now make it NOT NULL
ALTER TABLE orders ALTER COLUMN dining_option SET NOT NULL;
