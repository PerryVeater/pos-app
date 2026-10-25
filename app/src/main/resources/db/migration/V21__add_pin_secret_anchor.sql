-- V21: anchor recording which PIN fingerprint secret this database was
-- written with.
--
-- pos.pin.hmac-secret keys the deterministic fingerprint stored in
-- employee_pin_store_scope. Every claim row is therefore only meaningful
-- under the secret that produced it: change the secret and no submitted
-- PIN can ever match a claim again, because the login path looks rows up
-- by a fingerprint derived under the new key. The PINs themselves are not
-- recoverable (only salted PBKDF2 hashes are stored), so the new
-- fingerprints cannot be re-derived - the only way to re-materialize a
-- claim is for the employee to enter their PIN again.
--
-- Nothing in the schema noticed such a change before: the application
-- started normally and every login failed with the same generic 401 as a
-- wrong PIN, which is an outage indistinguishable from an attack. This
-- table gives startup one row to compare against, so the failure becomes
-- a refusal to start instead of a silent, total lockout.
--
-- pos_pin_secret_anchor:
--   * Exactly one row: ck_pps_single_row pins the id to 1 and the id is
--     the primary key, so no second anchor can exist. The row is created
--     on first startup and updated in place on a deliberate reset.
--   * secret_hash is a salted PBKDF2 hash of the secret itself, not the
--     secret and not a bare hash of it. Storing the secret would put the
--     key next to the data it protects; storing a plain digest would give
--     a dumped database a cheap offline oracle for guessing the secret.
--     Reusing the PIN hasher's format costs one 120k-iteration derivation
--     at startup and keeps that oracle expensive.
--
-- No change to existing rows: adding this table is safe on a populated
-- database and leaves no window in which data is missing.

CREATE TABLE pos_pin_secret_anchor (
    id          BIGINT       PRIMARY KEY,
    secret_hash VARCHAR(255) NOT NULL,
    anchored_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_pps_single_row CHECK (id = 1)
);
