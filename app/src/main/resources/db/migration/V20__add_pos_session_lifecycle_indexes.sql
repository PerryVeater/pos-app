-- V20: indexes for the POS session lifecycle.
-- Two access paths arrived with the M1 session work and neither is served
-- by an existing index: uk_pos_session_token_hash covers token_hash only,
-- and fk_pos_session_employee created no index on the referencing column
-- (PostgreSQL never does that implicitly).
--
--   * idx_pos_session_expires_at serves the purge job's batch select:
--       SELECT id FROM pos_session WHERE expires_at < ? ORDER BY id LIMIT ?
--     The sort stays in memory and costs little; the point of the index is
--     to avoid scanning the whole table on every run just to find the
--     handful of rows nobody can use any more.
--
--   * idx_pos_session_employee_live serves the per-employee session cap:
--       SELECT ... FROM pos_session
--        WHERE employee_id = ? AND revoked_at IS NULL AND expires_at > ?
--        ORDER BY created_at
--     It is partial on revoked_at IS NULL so revoked rows are not indexed
--     at all - the cap only ever looks at live sessions, and most rows in
--     a busy deployment are dead. The (employee_id, created_at) order
--     matches the ORDER BY that decides which session gets revoked.
--
-- No data change: indexes only, so this migration is safe to apply to a
-- populated database and leaves no window in which rows are missing.

CREATE INDEX idx_pos_session_expires_at ON pos_session (expires_at);

CREATE INDEX idx_pos_session_employee_live ON pos_session (employee_id, created_at)
    WHERE revoked_at IS NULL;
