-- V23: optimistic locking for orders and payments (D5).
-- Both rows are written through a read-modify-write cycle: a status transition
-- reads the current status, decides whether the move is legal, then writes. At
-- read-committed isolation nothing stopped two concurrent requests from both
-- reading PENDING and both writing, with the later write silently winning. A
-- lost update is bad enough; here it means two terminals each believe they
-- moved the order forward, and the audit trail claims a transition that the
-- surviving row does not show.
--
-- version is a concurrency counter, not a business revision. Hibernate
-- increments it on every UPDATE and appends "AND version = ?" to the WHERE
-- clause, so a transaction whose read has been superseded updates zero rows
-- and fails instead of overwriting. The application maps that failure to
-- 409 Conflict, and the caller's only correct response is to reload.
--
-- Adding the column with a non-volatile default rewrites no table in
-- PostgreSQL 11+: existing rows read the default from the catalogue, so this
-- is safe on a populated database. The default is kept deliberately. Hibernate
-- always writes the column explicitly for an @Version entity, so the default
-- only applies to rows inserted by tools, seeds and tests that bypass the
-- entity layer - exactly the case where NOT NULL without a default would turn
-- a harmless insert into a failure.
--
-- Only these two tables get a version. The catalog and the tenancy tables are
-- not written concurrently today, and a version column nobody writes is
-- cargo cult; each entity that grows a read-modify-write path later gets its
-- own migration.

ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
