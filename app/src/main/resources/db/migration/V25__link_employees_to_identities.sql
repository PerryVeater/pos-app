-- V25: link an employee to the identity that acts for them in the back office.
--
-- idp_subject holds the OIDC "sub" claim of the account that has been granted
-- this employee's back-office access. It is the join between the identity
-- provider (who someone is) and the domain (what they may touch), and it is the
-- only place that mapping exists: the token carries a subject, the employee row
-- answers "which organization", and no organization claim is trusted alongside
-- it. A brand claim in the token would be a second source of truth for the same
-- fact, and two sources eventually disagree - a token minted before a transfer
-- would still carry the old brand.
--
-- Nullable on purpose. Most employees are POS staff who never log into the back
-- office, and a NOT NULL column would force a synthetic identity for every one
-- of them. A row with NULL simply cannot be used to authenticate.
--
-- UNIQUE on purpose, and global rather than per organization. A subject that
-- could resolve to two employees would make the scope of every request depend on
-- which row the query happened to return. One identity, at most one employee.
--
-- Deliberately not backfilled. Unlike the timestamp conversion in V24, there is
-- no fact in the existing data that implies a subject: linking is an assertion
-- an administrator makes about a real account. Empty is the honest state.
--
-- The unique constraint gives the lookup its index, so no separate index is
-- created for findByIdpSubject.

ALTER TABLE employee ADD COLUMN idp_subject VARCHAR(255);

ALTER TABLE employee ADD CONSTRAINT uk_employee_idp_subject UNIQUE (idp_subject);
