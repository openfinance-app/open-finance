-- Complete, versioned change sets replace the legacy display-only snapshots.
ALTER TABLE operation_history ADD COLUMN action_state_json TEXT;
ALTER TABLE operation_history ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE operation_history_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_op_history_user_sequence ON operation_history(user_id, id DESC);
