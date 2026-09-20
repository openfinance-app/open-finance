-- Existing templates retain their currently scheduled day; previously lost anchors cannot be inferred.
ALTER TABLE recurring_transactions ADD COLUMN anchor_day INTEGER NOT NULL DEFAULT 1 CHECK (anchor_day BETWEEN 1 AND 31);
UPDATE recurring_transactions SET anchor_day = EXTRACT(DAY FROM next_occurrence::date)::integer;

ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE revoked_tokens (
    token_hash VARCHAR(64) PRIMARY KEY NOT NULL,
    expires_at BIGINT NOT NULL
);
CREATE INDEX idx_revoked_tokens_expiry ON revoked_tokens(expires_at);
