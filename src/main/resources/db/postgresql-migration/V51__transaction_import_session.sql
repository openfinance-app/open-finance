ALTER TABLE transactions ADD COLUMN import_session_id BIGINT REFERENCES import_sessions(id) ON DELETE SET NULL;
ALTER TABLE transactions_archive ADD COLUMN import_session_id BIGINT REFERENCES import_sessions(id) ON DELETE SET NULL;
CREATE INDEX idx_transactions_import_session ON transactions(import_session_id, user_id) WHERE import_session_id IS NOT NULL;
