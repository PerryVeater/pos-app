-- V2: product identity and lifecycle state.
-- sku:    stable, caller-supplied product identifier used by POS systems.
-- active: availability flag; products are deactivated instead of deleted.
--
-- Existing rows receive safe values before the constraints are enforced:
-- every product is backfilled with a unique placeholder SKU derived from its
-- id (ids are unique by primary key) and stays available for sale. V1 is
-- intentionally left untouched.

ALTER TABLE product ADD COLUMN sku VARCHAR(64);

UPDATE product SET sku = 'LEGACY-' || id WHERE sku IS NULL;

ALTER TABLE product ALTER COLUMN sku SET NOT NULL;

ALTER TABLE product ADD CONSTRAINT uk_product_sku UNIQUE (sku);

ALTER TABLE product ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
